package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.domain.timeline.IdGenerator
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal object TtmlCodec {
    private const val STYLING_NS = "http://www.w3.org/ns/ttml#styling"
    private val CLOCK = Regex("""(\d+):(\d{2}):(\d{2})(?:[.,](\d+))?""")
    private val OFFSET = Regex("""(\d+(?:\.\d+)?)(h|ms|m|s)""")

    fun parse(text: String, name: String, ids: IdGenerator): SubtitleParseResult {
        val root = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { isExpandEntityReferences = false }
            }
            factory.newDocumentBuilder().apply { setErrorHandler(null) }.parse(InputSource(StringReader(SubtitleText.normalize(text).trim()))).documentElement
        } catch (e: Exception) { return SubtitleParseResult.Failed(SubtitleError.Malformed) }
        if (root.localName != "tt") return SubtitleParseResult.Failed(SubtitleError.Malformed)
        val language = root.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang").ifBlank { null }?.takeIf { it != "und" }
        val warnings = mutableListOf<SubtitleWarning>()
        val trackId = ids.next()
        val cues = mutableListOf<SubtitleCue>()
        val paragraphs = root.getElementsByTagNameNS("*", "p")
        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as Element
            val start = time(p.getAttribute("begin")); val dur = time(p.getAttribute("dur"))
            val end = time(p.getAttribute("end")) ?: if (start != null && dur != null) start + dur else null
            if (start == null || end == null || SubtitleTiming.validate(start, end) != null) { warnings += SubtitleWarning(WarningReason.INVALID_TIMING); continue }
            val content = collect(p).replace(Regex(" *\n *"), "\n").trim()
            if (content.isBlank()) { warnings += SubtitleWarning(WarningReason.INVALID_BLOCK); continue }
            cues += SubtitleCue(ids.next(), start, end, content)
        }
        if (cues.isEmpty()) return SubtitleParseResult.Failed(SubtitleError.Malformed)
        return SubtitleParseResult.Parsed(SubtitleTrack(trackId, name, cues, language = language, sourceFormat = SubtitleFormat.TTML), warnings)
    }

    private fun time(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        CLOCK.matchEntire(s)?.let { return SubtitleText.clockMs(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4]) }
        OFFSET.matchEntire(s)?.let {
            val v = it.groupValues[1].toDouble()
            return Math.round(v * when (it.groupValues[2]) { "h" -> 3_600_000.0; "m" -> 60_000.0; "s" -> 1_000.0; else -> 1.0 })
        }
        return null   // frame/tick based timings need the document's frame rate and are not supported
    }

    private fun collect(node: Node): String = buildString {
        var child = node.firstChild
        while (child != null) {
            when {
                child.nodeType == Node.TEXT_NODE -> append(child.nodeValue.replace(Regex("\\s+"), " "))
                child is Element && child.localName == "br" -> append('\n')
                child is Element -> {
                    val italic = child.getAttributeNS(STYLING_NS, "fontStyle") == "italic"
                    val bold = child.getAttributeNS(STYLING_NS, "fontWeight") == "bold"
                    val underline = child.getAttributeNS(STYLING_NS, "textDecoration") == "underline"
                    if (italic) append("<i>"); if (bold) append("<b>"); if (underline) append("<u>")
                    append(collect(child))
                    if (underline) append("</u>"); if (bold) append("</b>"); if (italic) append("</i>")
                }
            }
            child = child.nextSibling
        }
    }

    fun serialize(track: SubtitleTrack): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:tts=\"$STYLING_NS\" xml:lang=\"${track.language ?: "und"}\">\n<body>\n<div>\n")
        track.effectiveCues().forEach { c ->
            append("<p begin=\"").append(SubtitleText.format(c.startMs, '.')).append("\" end=\"").append(SubtitleText.format(c.endMs, '.')).append("\">")
            append(markup(c.text)).append("</p>\n")
        }
        append("</div>\n</body>\n</tt>\n")
    }

    private fun markup(text: String): String {
        val escaped = SubtitleText.escapeMarkup(text).replace("&lt;", "&lt;")
        return escaped.replace("\n", "<br/>")
            .replace("<i>", "<span tts:fontStyle=\"italic\">").replace("<b>", "<span tts:fontWeight=\"bold\">").replace("<u>", "<span tts:textDecoration=\"underline\">")
            .replace("</i>", "</span>").replace("</b>", "</span>").replace("</u>", "</span>")
    }
}
