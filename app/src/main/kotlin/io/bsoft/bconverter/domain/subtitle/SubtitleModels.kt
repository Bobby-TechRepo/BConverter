package io.bsoft.bconverter.domain.subtitle

import kotlin.math.max

/** The subtitle file formats the editor can read and write. */
enum class SubtitleFormat(val extension: String, val mimeType: String) {
    SRT("srt", "application/x-subrip"),
    ASS("ass", "text/x-ssa"),
    SSA("ssa", "text/x-ssa"),
    WEBVTT("vtt", "text/vtt"),
    TTML("ttml", "application/ttml+xml");

    companion object {
        fun fromFileName(name: String): SubtitleFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "srt" -> SRT
            "ass" -> ASS
            "ssa" -> SSA
            "vtt" -> WEBVTT
            "ttml", "dfxp" -> TTML
            else -> null
        }
    }
}

/** Where on the picture text sits; the value is the numeric-keypad position used by ASS (7 8 9 / 4 5 6 / 1 2 3). */
enum class SubtitleAlignment(val numpad: Int) {
    BOTTOM_LEFT(1), BOTTOM_CENTER(2), BOTTOM_RIGHT(3),
    MIDDLE_LEFT(4), MIDDLE_CENTER(5), MIDDLE_RIGHT(6),
    TOP_LEFT(7), TOP_CENTER(8), TOP_RIGHT(9);

    val isTop: Boolean get() = numpad >= 7
    val isMiddle: Boolean get() = numpad in 4..6
    val isBottom: Boolean get() = numpad <= 3

    companion object { fun fromNumpad(n: Int): SubtitleAlignment? = entries.firstOrNull { it.numpad == n } }
}

/** A named subtitle style. Colours are ARGB; sizes are in the units of the track's play resolution (ASS convention). */
data class SubtitleStyle(
    val name: String = DEFAULT_NAME,
    val fontFamily: String? = null,
    val fontSize: Float = 48f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val primaryColor: Int = 0xFFFFFFFF.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    val backColor: Int = 0x80000000.toInt(),
    /** True: an opaque box is drawn behind the text (ASS border style 3); false: outline and shadow. */
    val boxed: Boolean = false,
    val outlineWidth: Float = 2f,
    val shadow: Float = 1f,
    val alignment: SubtitleAlignment = SubtitleAlignment.BOTTOM_CENTER,
    val marginL: Int = 10,
    val marginR: Int = 10,
    val marginV: Int = 30,
) { companion object { const val DEFAULT_NAME = "Default" } }

/**
 * One subtitle: shown from [startMs] to [endMs]. [text] is plain with optional `<i>`, `<b>` and `<u>` markup and `\n` line breaks.
 * [styleName] points into the track's styles; [assOverride] keeps ASS override tags (e.g. `\an8\pos(100,50)`) so they are not lost.
 */
data class SubtitleCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val styleName: String? = null,
    val assOverride: String? = null,
) {
    init { require(SubtitleTiming.validate(startMs, endMs) == null) { "invalid subtitle interval $startMs..$endMs" } }

    val durationMs: Long get() = endMs - startMs
}

/** A subtitle track of a project. [offsetMs] shifts every cue when the track is shown or exported; the cues themselves are never rewritten. */
data class SubtitleTrack(
    val id: String,
    val name: String,
    val cues: List<SubtitleCue> = emptyList(),
    val styles: Map<String, SubtitleStyle> = emptyMap(),
    val language: String? = null,
    val title: String? = null,
    val offsetMs: Long = 0,
    val sourceFormat: SubtitleFormat? = null,
    val playResX: Int? = null,
    val playResY: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    /** What happens to this track when the project is exported. */
    val exportMode: SubtitleExportMode = SubtitleExportMode.SOFT,
) {
    /** The cues as they are really shown: offset applied, sorted, trimmed at time zero, dropped if they end before it. */
    fun effectiveCues(): List<SubtitleCue> = cues.sortedBy { it.startMs }.mapNotNull { c ->
        val end = c.endMs + offsetMs
        if (end <= 0) null else c.copy(startMs = max(0L, c.startMs + offsetMs), endMs = end)
    }
}

enum class TimingError { NegativeStart, EndNotAfterStart, TooShort }

object SubtitleTiming {
    fun validate(startMs: Long, endMs: Long, minDurationMs: Long = 1): TimingError? = when {
        startMs < 0 -> TimingError.NegativeStart
        endMs <= startMs -> TimingError.EndNotAfterStart
        endMs - startMs < minDurationMs -> TimingError.TooShort
        else -> null
    }
}

enum class SubtitleError { Empty, UnknownFormat, Malformed }

enum class WarningReason { INVALID_BLOCK, INVALID_TIMING, TAGS_SIMPLIFIED }

/** Something in a file that was skipped or simplified; [line] is 1-based where known. */
data class SubtitleWarning(val reason: WarningReason, val line: Int? = null)

sealed interface SubtitleParseResult {
    data class Parsed(val track: SubtitleTrack, val warnings: List<SubtitleWarning> = emptyList()) : SubtitleParseResult
    data class Failed(val error: SubtitleError) : SubtitleParseResult
}

/** Styling that a format cannot carry; reported before converting so nothing is dropped silently. */
enum class StyleFeature { NAMED_STYLES, POSITIONING }
