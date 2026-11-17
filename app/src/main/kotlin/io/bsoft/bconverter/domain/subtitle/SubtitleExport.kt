package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.domain.model.ContainerFormat

/** OFF: not exported. SOFT: a separate track the player can switch off. BURNED: drawn into the picture. A track is exactly one of these. */
enum class SubtitleExportMode { OFF, SOFT, BURNED }

/** The subtitle codecs a container can hold; [fileFormat] is the file the track is written to before FFmpeg reads it. */
enum class SoftSubtitleCodec(val ffmpegName: String, val fileFormat: SubtitleFormat) {
    MOV_TEXT("mov_text", SubtitleFormat.SRT),
    SRT("srt", SubtitleFormat.SRT),
    ASS("ass", SubtitleFormat.ASS),
    WEBVTT("webvtt", SubtitleFormat.WEBVTT),
}

sealed interface SubtitleExportIssue {
    /** The container cannot carry text subtitles: burn the track in, switch it off or pick another container. */
    data class SoftNotSupported(val trackId: String, val container: ContainerFormat) : SubtitleExportIssue
    /** Colours, fonts and positions of the track cannot be stored in this codec. */
    data class StyleSimplified(val trackId: String, val codec: SoftSubtitleCodec) : SubtitleExportIssue
}

data class SoftSubtitle(val track: SubtitleTrack, val codec: SoftSubtitleCodec)

data class SubtitleExportPlan(val soft: List<SoftSubtitle>, val burned: List<SubtitleTrack>, val issues: List<SubtitleExportIssue>) {
    val blocking: List<SubtitleExportIssue> get() = issues.filterIsInstance<SubtitleExportIssue.SoftNotSupported>()
    val isEmpty: Boolean get() = soft.isEmpty() && burned.isEmpty()
}

object SubtitleExportPlanner {
    /** Each track lands in at most one group, so nothing is exported twice. Tracks with nothing to show are left out. */
    fun plan(tracks: List<SubtitleTrack>, container: ContainerFormat): SubtitleExportPlan {
        val soft = mutableListOf<SoftSubtitle>()
        val burned = mutableListOf<SubtitleTrack>()
        val issues = mutableListOf<SubtitleExportIssue>()
        tracks.filter { it.effectiveCues().isNotEmpty() }.forEach { track ->
            when (track.exportMode) {
                SubtitleExportMode.OFF -> Unit
                SubtitleExportMode.BURNED -> burned += track
                SubtitleExportMode.SOFT -> {
                    val styled = hasStyling(track)
                    val codec = softCodec(container, styled)
                    if (codec == null) issues += SubtitleExportIssue.SoftNotSupported(track.id, container) else {
                        soft += SoftSubtitle(track, codec)
                        if (styled && codec != SoftSubtitleCodec.ASS) issues += SubtitleExportIssue.StyleSimplified(track.id, codec)
                    }
                }
            }
        }
        return SubtitleExportPlan(soft, burned, issues)
    }

    fun softCodec(container: ContainerFormat, styled: Boolean): SoftSubtitleCodec? = when (container) {
        ContainerFormat.MP4, ContainerFormat.MOV, ContainerFormat.THREE_GP -> SoftSubtitleCodec.MOV_TEXT
        ContainerFormat.MKV -> if (styled) SoftSubtitleCodec.ASS else SoftSubtitleCodec.SRT
        ContainerFormat.WEBM -> SoftSubtitleCodec.WEBVTT
        ContainerFormat.AVI, ContainerFormat.MPEG_TS -> null
    }

    /** True when the track carries more than plain text: a non-default style or ASS override tags. */
    fun hasStyling(track: SubtitleTrack): Boolean =
        track.cues.any { it.assOverride != null } || track.styles.values.any { it != SubtitleStyle(name = it.name) }
}

/** Container-friendly three-letter language codes (ISO 639-2) from the two- or three-letter codes users and files carry. */
object LanguageCodes {
    private val twoToThree = mapOf(
        "en" to "eng", "fr" to "fra", "es" to "spa", "de" to "deu", "it" to "ita", "pt" to "por", "ru" to "rus", "zh" to "zho", "ja" to "jpn", "ko" to "kor",
        "ar" to "ara", "hi" to "hin", "bn" to "ben", "tr" to "tur", "nl" to "nld", "pl" to "pol", "sv" to "swe", "da" to "dan", "fi" to "fin", "no" to "nor",
        "cs" to "ces", "el" to "ell", "he" to "heb", "hu" to "hun", "id" to "ind", "ms" to "msa", "th" to "tha", "vi" to "vie", "uk" to "ukr", "ro" to "ron",
        "fa" to "fas", "ur" to "urd", "sw" to "swa", "ha" to "hau", "yo" to "yor", "ig" to "ibo", "af" to "afr", "ta" to "tam", "te" to "tel",
    )

    fun iso639_2(code: String?): String? {
        val base = code?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (base.length) {
            2 -> twoToThree[base]
            3 -> if (base.all { it in 'a'..'z' }) base else null
            else -> null
        }
    }
}
