package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.domain.timeline.IdGenerator

internal object SrtCodec {
    fun parse(text: String, name: String, ids: IdGenerator): SubtitleParseResult {
        val warnings = mutableListOf<SubtitleWarning>()
        val raws = CueBlocks.read(CueBlocks.split(SubtitleText.normalize(text)), warnings)
        if (raws.isEmpty()) return SubtitleParseResult.Failed(SubtitleError.Malformed)
        val trackId = ids.next()
        val cues = raws.map { SubtitleCue(ids.next(), it.startMs, it.endMs, it.text) }
        return SubtitleParseResult.Parsed(SubtitleTrack(trackId, name, cues, sourceFormat = SubtitleFormat.SRT), warnings)
    }

    fun serialize(track: SubtitleTrack): String = buildString {
        track.effectiveCues().forEachIndexed { i, c ->
            append(i + 1).append('\n')
            append(SubtitleText.format(c.startMs, ',')).append(" --> ").append(SubtitleText.format(c.endMs, ',')).append('\n')
            append(c.text).append("\n\n")
        }
    }
}

internal object WebVttCodec {
    private val HEADER = Regex("^WEBVTT(?:[ \t].*)?$")

    fun parse(text: String, name: String, ids: IdGenerator): SubtitleParseResult {
        val normalized = SubtitleText.normalize(text)
        val blocks = CueBlocks.split(normalized)
        val first = blocks.firstOrNull() ?: return SubtitleParseResult.Failed(SubtitleError.Empty)
        if (!HEADER.matches(first.second.first())) return SubtitleParseResult.Failed(SubtitleError.Malformed)
        val warnings = mutableListOf<SubtitleWarning>()
        val cueBlocks = blocks.drop(1).filterNot { (_, lines) ->
            val head = lines.first()
            head == "NOTE" || head.startsWith("NOTE ") || head.startsWith("NOTE\t") || head == "STYLE" || head == "REGION"
        }
        val raws = CueBlocks.read(cueBlocks, warnings)
        if (raws.isEmpty()) return SubtitleParseResult.Failed(SubtitleError.Malformed)
        val trackId = ids.next()
        val cues = raws.map { SubtitleCue(ids.next(), it.startMs, it.endMs, SubtitleText.stripTagsKeepBasic(it.text)) }
        return SubtitleParseResult.Parsed(SubtitleTrack(trackId, name, cues, sourceFormat = SubtitleFormat.WEBVTT), warnings)
    }

    fun serialize(track: SubtitleTrack): String = buildString {
        append("WEBVTT\n\n")
        track.effectiveCues().forEach { c ->
            append(SubtitleText.format(c.startMs, '.')).append(" --> ").append(SubtitleText.format(c.endMs, '.')).append('\n')
            append(SubtitleText.escapeMarkup(c.text)).append("\n\n")
        }
    }
}
