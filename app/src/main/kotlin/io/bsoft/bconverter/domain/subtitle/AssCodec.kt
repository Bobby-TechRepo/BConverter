package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.domain.timeline.IdGenerator
import java.util.Locale
import kotlin.math.roundToLong

/** ASS (v4.00+) and SSA (v4.00) share one reader/writer; the differences are columns, colour notation and alignment numbers. */
internal object AssCodec {
    private val DIALOGUE_TIME = Regex("""(\d+):(\d{2}):(\d{2})[.](\d{1,3})""")
    private val DEFAULT_STYLE_COLUMNS = listOf(
        "name", "fontname", "fontsize", "primarycolour", "secondarycolour", "outlinecolour", "backcolour", "bold", "italic", "underline",
        "strikeout", "scalex", "scaley", "spacing", "angle", "borderstyle", "outline", "shadow", "alignment", "marginl", "marginr", "marginv", "encoding",
    )

    // ---------------- reading ----------------
    fun parse(text: String, format: SubtitleFormat, name: String, ids: IdGenerator): SubtitleParseResult {
        val ssa = format == SubtitleFormat.SSA
        val warnings = mutableListOf<SubtitleWarning>()
        var section = ""
        var styleColumns = DEFAULT_STYLE_COLUMNS
        var eventColumns = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        var title: String? = null; var playX: Int? = null; var playY: Int? = null
        val styles = linkedMapOf<String, SubtitleStyle>()
        val dialogues = mutableListOf<Pair<Int, String>>()
        SubtitleText.normalize(text).split("\n").forEachIndexed { index, raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith(";") -> Unit
                line.startsWith("[") && line.endsWith("]") -> section = line.lowercase()
                section == "[script info]" -> {
                    val key = line.substringBefore(':').trim().lowercase(); val value = line.substringAfter(':', "").trim()
                    when (key) { "title" -> title = value.ifEmpty { null }; "playresx" -> playX = value.toIntOrNull(); "playresy" -> playY = value.toIntOrNull() }
                }
                section.endsWith("styles]") -> when {
                    line.startsWith("Format:", ignoreCase = true) -> styleColumns = columns(line)
                    line.startsWith("Style:", ignoreCase = true) -> parseStyle(line.substringAfter(':').trim(), styleColumns, ssa)?.let { styles[it.name] = it }
                }
                section == "[events]" -> when {
                    line.startsWith("Format:", ignoreCase = true) -> eventColumns = columns(line)
                    line.startsWith("Dialogue:", ignoreCase = true) -> dialogues += (index + 1) to line.substringAfter(':').trimStart()
                }
            }
        }
        val startIdx = eventColumns.indexOf("start"); val endIdx = eventColumns.indexOf("end")
        val styleIdx = eventColumns.indexOf("style"); val textIdx = eventColumns.indexOf("text")
        if (startIdx < 0 || endIdx < 0 || textIdx < 0) return SubtitleParseResult.Failed(SubtitleError.Malformed)
        val trackId = ids.next()
        var simplified = false
        val cues = mutableListOf<SubtitleCue>()
        for ((lineNo, body) in dialogues) {
            val parts = body.split(",", limit = eventColumns.size)
            if (parts.size < eventColumns.size) { warnings += SubtitleWarning(WarningReason.INVALID_BLOCK, lineNo); continue }
            val start = time(parts[startIdx].trim()); val end = time(parts[endIdx].trim())
            if (start == null || end == null || SubtitleTiming.validate(start, end) != null) { warnings += SubtitleWarning(WarningReason.INVALID_TIMING, lineNo); continue }
            val converted = convertText(parts[textIdx])
            if (converted.dropped) simplified = true
            if (converted.text.isBlank()) { warnings += SubtitleWarning(WarningReason.INVALID_BLOCK, lineNo); continue }
            val style = if (styleIdx >= 0) parts[styleIdx].trim().trimStart('*').ifEmpty { null } else null
            cues += SubtitleCue(ids.next(), start, end, converted.text, style, converted.override)
        }
        if (simplified) warnings += SubtitleWarning(WarningReason.TAGS_SIMPLIFIED)
        if (cues.isEmpty()) return SubtitleParseResult.Failed(SubtitleError.Malformed)
        val track = SubtitleTrack(trackId, title ?: name, cues, styles, title = title, sourceFormat = format, playResX = playX, playResY = playY)
        return SubtitleParseResult.Parsed(track, warnings)
    }

    private fun columns(line: String) = line.substringAfter(':').split(",").map { it.trim().lowercase() }

    private fun time(s: String): Long? {
        val m = DIALOGUE_TIME.matchEntire(s) ?: return null
        val fraction = m.groupValues[4]
        // ASS fractions are centiseconds; a three-digit fraction is milliseconds.
        val ms = if (fraction.length >= 3) fraction.take(3).toLong() else fraction.padEnd(2, '0').toLong() * 10
        return (m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()) * 1000 + ms
    }

    private fun parseStyle(body: String, columns: List<String>, ssa: Boolean): SubtitleStyle? {
        val v = body.split(",", limit = columns.size).map { it.trim() }
        fun get(key: String) = columns.indexOf(key).takeIf { it >= 0 && it < v.size }?.let { v[it] }
        val name = get("name") ?: return null
        val rawAlign = get("alignment")?.toIntOrNull() ?: 2
        val alignment = (if (ssa) ssaToNumpad(rawAlign) else rawAlign).let { SubtitleAlignment.fromNumpad(it) } ?: SubtitleAlignment.BOTTOM_CENTER
        fun flag(key: String) = get(key)?.toIntOrNull()?.let { it != 0 } ?: false
        val d = SubtitleStyle(name)
        return SubtitleStyle(
            name = name, fontFamily = get("fontname")?.ifEmpty { null }, fontSize = get("fontsize")?.toFloatOrNull() ?: d.fontSize,
            bold = flag("bold"), italic = flag("italic"), underline = flag("underline"),
            primaryColor = get("primarycolour")?.let(::color) ?: d.primaryColor,
            outlineColor = (get("outlinecolour") ?: get("tertiarycolour"))?.let(::color) ?: d.outlineColor,
            backColor = get("backcolour")?.let(::color) ?: d.backColor,
            boxed = get("borderstyle")?.toIntOrNull() == 3,
            outlineWidth = get("outline")?.toFloatOrNull() ?: d.outlineWidth, shadow = get("shadow")?.toFloatOrNull() ?: d.shadow,
            alignment = alignment,
            marginL = get("marginl")?.toIntOrNull() ?: d.marginL, marginR = get("marginr")?.toIntOrNull() ?: d.marginR, marginV = get("marginv")?.toIntOrNull() ?: d.marginV,
        )
    }

    /** `&HAABBGGRR` (or the decimal form of the same number) to ARGB; ASS alpha 0 is opaque. */
    private fun color(raw: String): Int? {
        val s = raw.trim()
        val v = (if (s.startsWith("&H", ignoreCase = true)) s.drop(2).trimEnd('&').toLongOrNull(16) else s.toLongOrNull()) ?: return null
        val a = (v shr 24 and 0xFF).toInt(); val b = (v shr 16 and 0xFF).toInt(); val g = (v shr 8 and 0xFF).toInt(); val r = (v and 0xFF).toInt()
        return ((255 - a) shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun ssaToNumpad(n: Int) = when (n) { 1, 2, 3 -> n; 5 -> 7; 6 -> 8; 7 -> 9; 9 -> 4; 10 -> 5; 11 -> 6; else -> 2 }
    private fun numpadToSsa(n: Int) = when (n) { 1, 2, 3 -> n; 7 -> 5; 8 -> 6; 9 -> 7; 4 -> 9; 5 -> 10; 6 -> 11; else -> 2 }

    private class Converted(val text: String, val override: String?, val dropped: Boolean)

    /** `\N` to newline, `{\i1}`-style toggles to markup, leading overrides kept aside, other inline overrides dropped. */
    private fun convertText(raw: String): Converted {
        val out = StringBuilder(); val kept = StringBuilder(); var dropped = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '{') {
                val close = raw.indexOf('}', i)
                if (close > i) {
                    val atStart = out.isEmpty()
                    for (tag in tags(raw.substring(i + 1, close))) {
                        val mark = when (tag) { "i1" -> "<i>"; "i0" -> "</i>"; "b1" -> "<b>"; "b0" -> "</b>"; "u1" -> "<u>"; "u0" -> "</u>"; else -> null }
                        when { mark != null -> out.append(mark); atStart -> kept.append('\\').append(tag); else -> dropped = true }
                    }
                    i = close + 1; continue
                }
            }
            if (ch == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) { 'N', 'n' -> { out.append('\n'); i += 2; continue }; 'h' -> { out.append(' '); i += 2; continue } }
            }
            out.append(ch); i++
        }
        return Converted(out.toString().trim(), kept.toString().ifEmpty { null }, dropped)
    }

    /** Splits an override block into its tags, keeping parenthesised arguments (which may contain backslashes) together. */
    private fun tags(block: String): List<String> {
        val result = mutableListOf<String>(); val cur = StringBuilder(); var depth = 0
        for (c in block) {
            when {
                c == '\\' && depth == 0 -> { if (cur.isNotEmpty()) result += cur.toString(); cur.clear() }
                else -> { if (c == '(') depth++ else if (c == ')') depth = (depth - 1).coerceAtLeast(0); cur.append(c) }
            }
        }
        if (cur.isNotEmpty()) result += cur.toString()
        return result
    }

    // ---------------- writing ----------------
    fun serialize(track: SubtitleTrack, format: SubtitleFormat): String {
        val ssa = format == SubtitleFormat.SSA
        val cues = track.effectiveCues()
        val styles = linkedMapOf<String, SubtitleStyle>().apply {
            putAll(track.styles)
            if (isEmpty()) put(SubtitleStyle.DEFAULT_NAME, SubtitleStyle())
            cues.mapNotNull { it.styleName }.filter { it !in this }.forEach { put(it, SubtitleStyle(it)) }
        }
        val defaultName = if (SubtitleStyle.DEFAULT_NAME in styles) SubtitleStyle.DEFAULT_NAME else styles.keys.first()
        return buildString {
            append("[Script Info]\n")
            track.title?.let { append("Title: ").append(it).append('\n') }
            append(if (ssa) "ScriptType: v4.00\n" else "ScriptType: v4.00+\n")
            append("PlayResX: ").append(track.playResX ?: 1920).append('\n')
            append("PlayResY: ").append(track.playResY ?: 1080).append("\n\n")
            if (ssa) {
                append("[V4 Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, TertiaryColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, AlphaLevel, Encoding\n")
                styles.values.forEach { s ->
                    append("Style: ").append(listOf(s.name, s.fontFamily ?: "Arial", num(s.fontSize), ssaColor(s.primaryColor), "255", ssaColor(s.outlineColor), ssaColor(s.backColor),
                        if (s.bold) "-1" else "0", if (s.italic) "-1" else "0", if (s.boxed) "3" else "1", num(s.outlineWidth), num(s.shadow),
                        numpadToSsa(s.alignment.numpad), s.marginL, s.marginR, s.marginV, "0", "1").joinToString(",")).append('\n')
                }
                append("\n[Events]\nFormat: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
            } else {
                append("[V4+ Styles]\nFormat: ").append(DEFAULT_STYLE_COLUMNS.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() }.let(::pascal) }).append('\n')
                styles.values.forEach { s ->
                    append("Style: ").append(listOf(s.name, s.fontFamily ?: "Arial", num(s.fontSize), assColor(s.primaryColor), "&H000000FF", assColor(s.outlineColor), assColor(s.backColor),
                        if (s.bold) "-1" else "0", if (s.italic) "-1" else "0", if (s.underline) "-1" else "0", "0", "100", "100", "0", "0", if (s.boxed) "3" else "1",
                        num(s.outlineWidth), num(s.shadow), s.alignment.numpad, s.marginL, s.marginR, s.marginV, "1").joinToString(",")).append('\n')
                }
                append("\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
            }
            cues.forEach { c ->
                val lead = if (ssa) "Marked=0" else "0"
                append("Dialogue: ").append(lead).append(',').append(assTime(c.startMs)).append(',').append(assTime(c.endMs)).append(',')
                append(c.styleName ?: defaultName).append(",,0,0,0,,").append(assText(c)).append('\n')
            }
        }
    }

    private fun pascal(s: String) = when (s) { "Primarycolour" -> "PrimaryColour"; "Secondarycolour" -> "SecondaryColour"; "Outlinecolour" -> "OutlineColour"; "Backcolour" -> "BackColour"
        "Strikeout" -> "StrikeOut"; "Scalex" -> "ScaleX"; "Scaley" -> "ScaleY"; "Borderstyle" -> "BorderStyle"; "Marginl" -> "MarginL"; "Marginr" -> "MarginR"; "Marginv" -> "MarginV"; else -> s }

    private fun assText(c: SubtitleCue): String {
        val body = c.text.replace("<i>", "{\\i1}").replace("</i>", "{\\i0}").replace("<b>", "{\\b1}").replace("</b>", "{\\b0}")
            .replace("<u>", "{\\u1}").replace("</u>", "{\\u0}").replace("\n", "\\N")
        return (c.assOverride?.let { "{$it}" } ?: "") + body
    }

    private fun num(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()
    private fun assTime(ms: Long): String {
        val cs = (ms / 10.0).roundToLong()
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", cs / 360_000, cs / 6_000 % 60, cs / 100 % 60, cs % 100)
    }
    private fun assColor(argb: Int): String {
        val a = 255 - (argb ushr 24 and 0xFF)
        return String.format(Locale.ROOT, "&H%02X%02X%02X%02X", a, argb and 0xFF, argb shr 8 and 0xFF, argb shr 16 and 0xFF)
    }
    private fun ssaColor(argb: Int): String = ((argb and 0xFF shl 16) or (argb shr 8 and 0xFF shl 8) or (argb shr 16 and 0xFF)).toString()
}
