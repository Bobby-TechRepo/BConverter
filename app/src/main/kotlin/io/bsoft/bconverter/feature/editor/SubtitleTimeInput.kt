package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import java.util.Locale

/** Reading and writing times in text fields: `[h:]m:ss.mmm`, plain seconds, `,` or `.` as the decimal mark. */
object SubtitleTimeInput {
    fun format(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val millis = total % 1000
        val s = total / 1000 % 60
        val m = total / 60_000 % 60
        val h = total / 3_600_000
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, millis) else String.format(Locale.ROOT, "%d:%02d.%03d", m, s, millis)
    }

    fun formatSigned(ms: Long): String = (if (ms < 0) "-" else "") + format(kotlin.math.abs(ms))

    fun parseSigned(text: String): Long? {
        val t = text.trim()
        return when {
            t.startsWith("-") -> parse(t.drop(1))?.let { -it }
            t.startsWith("+") -> parse(t.drop(1))
            else -> parse(t)
        }
    }

    fun parse(text: String): Long? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        val parts = t.split(':')
        if (parts.size > 3 || parts.any { it.isEmpty() }) return null
        val secondsPart = parts.last()
        val (wholeText, fracText) = secondsPart.split('.', limit = 2).let { it[0] to it.getOrNull(1) }
        if (secondsPart.count { it == '.' } > 1 || !wholeText.all { it.isDigit() } || wholeText.isEmpty()) return null
        val millis = when {
            fracText == null -> 0L
            fracText.isEmpty() || fracText.length > 3 || !fracText.all { it.isDigit() } -> return null
            else -> fracText.padEnd(3, '0').toLong()
        }
        val seconds = wholeText.toLongOrNull() ?: return null
        val higher = parts.dropLast(1).map { if (it.all(Char::isDigit)) it.toLong() else return null }
        val (hours, minutes) = when (higher.size) { 0 -> 0L to 0L; 1 -> 0L to higher[0]; else -> higher[0] to higher[1] }
        if (parts.size > 1 && seconds >= 60) return null
        if (parts.size == 3 && minutes >= 60) return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }
}

/** How far the subtitle tracks reach on the timeline. */
object SubtitleTimelineExtent {
    fun endMs(tracks: List<SubtitleTrack>): Long = tracks.maxOfOrNull { t -> t.effectiveCues().maxOfOrNull { it.endMs } ?: 0L } ?: 0L
}
