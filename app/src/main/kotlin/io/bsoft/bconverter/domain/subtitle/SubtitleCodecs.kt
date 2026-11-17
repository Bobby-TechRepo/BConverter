package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.timeline.UuidIdGenerator

/** Reads, detects and writes every supported subtitle format (spec: subtitle foundation). Pure text in, text out. */
object SubtitleCodecs {
    private val SRT_TIME = Regex("""\d+:\d{2}:\d{2},\d{1,3}\s*-->""")

    /** The format of [text] judged by its content; null when it does not look like any supported subtitle file. */
    fun detect(text: String, fileName: String? = null): SubtitleFormat? {
        val t = text.removePrefix("﻿").trimStart()
        return when {
            t.startsWith("WEBVTT") -> SubtitleFormat.WEBVTT
            t.startsWith("<") && Regex("<(?:\\w+:)?tt[\\s>]").containsMatchIn(t.take(2_000)) -> SubtitleFormat.TTML
            t.contains("[Script Info]", ignoreCase = true) || t.contains("[Events]", ignoreCase = true) -> when {
                t.contains("[V4+ Styles]", ignoreCase = true) || t.contains("ScriptType: v4.00+", ignoreCase = true) -> SubtitleFormat.ASS
                t.contains("[V4 Styles]", ignoreCase = true) || t.contains("ScriptType: v4.00", ignoreCase = true) -> SubtitleFormat.SSA
                else -> SubtitleFormat.ASS
            }
            SRT_TIME.containsMatchIn(t) -> SubtitleFormat.SRT
            else -> null
        }
    }

    /** Parses [text] as [format], or as the detected format (falling back to the file extension). Never throws on bad input. */
    fun parse(text: String, format: SubtitleFormat? = null, fileName: String? = null, name: String = "", ids: IdGenerator = UuidIdGenerator): SubtitleParseResult {
        if (text.isBlank()) return SubtitleParseResult.Failed(SubtitleError.Empty)
        val resolved = format ?: detect(text) ?: fileName?.let { SubtitleFormat.fromFileName(it) } ?: return SubtitleParseResult.Failed(SubtitleError.UnknownFormat)
        return try {
            val result = when (resolved) {
                SubtitleFormat.SRT -> SrtCodec.parse(text, name, ids)
                SubtitleFormat.WEBVTT -> WebVttCodec.parse(text, name, ids)
                SubtitleFormat.ASS, SubtitleFormat.SSA -> AssCodec.parse(text, resolved, name, ids)
                SubtitleFormat.TTML -> TtmlCodec.parse(text, name, ids)
            }
            // Files are not always in time order; the editor and the writers expect them sorted.
            if (result is SubtitleParseResult.Parsed) result.copy(track = result.track.copy(cues = result.track.cues.sortedBy { it.startMs })) else result
        } catch (e: RuntimeException) {
            SubtitleParseResult.Failed(SubtitleError.Malformed)
        }
    }

    /** The track as text in [format], with the track offset applied. */
    fun serialize(track: SubtitleTrack, format: SubtitleFormat): String = when (format) {
        SubtitleFormat.SRT -> SrtCodec.serialize(track)
        SubtitleFormat.WEBVTT -> WebVttCodec.serialize(track)
        SubtitleFormat.ASS, SubtitleFormat.SSA -> AssCodec.serialize(track, format)
        SubtitleFormat.TTML -> TtmlCodec.serialize(track)
    }

    /** Styling [track] has that [target] cannot hold: shown to the user before converting so nothing is lost silently. */
    fun lostInConversion(track: SubtitleTrack, target: SubtitleFormat): Set<StyleFeature> {
        if (target == SubtitleFormat.ASS || target == SubtitleFormat.SSA) return emptySet()
        return buildSet {
            if (track.styles.isNotEmpty() || track.cues.any { it.styleName != null }) add(StyleFeature.NAMED_STYLES)
            if (track.cues.any { it.assOverride != null }) add(StyleFeature.POSITIONING)
        }
    }
}
