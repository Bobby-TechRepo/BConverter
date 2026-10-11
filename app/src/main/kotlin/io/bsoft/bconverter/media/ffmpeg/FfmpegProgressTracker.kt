package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.media.api.MediaProcessingProgress

/**
 * Turns FFmpeg output lines into progress. Understands both `-progress pipe:1` key=value blocks
 * (emitted when a block ends) and the classic `frame=… time=HH:MM:SS.xx … speed=1.5x` status line.
 */
class FfmpegProgressTracker(private val totalDurationMs: Long?) {
    private var processedMs = 0L
    private var speed: Double? = null

    fun accept(line: String): MediaProcessingProgress? {
        val text = line.trim()
        if (text.isEmpty()) return null

        CLASSIC_TIME.find(text)?.let { m ->
            val (h, min, sec) = m.destructured
            processedMs = (h.toLong() * 3600 + min.toLong() * 60) * 1000 + (sec.toDouble() * 1000).toLong()
            speed = CLASSIC_SPEED.find(text)?.groupValues?.get(1)?.let { parseSpeed(it) }
            return snapshot(done = false)
        }

        val eq = text.indexOf('=')
        if (eq <= 0) return null
        val key = text.substring(0, eq)
        val value = text.substring(eq + 1).trim()
        when (key) {
            // out_time_ms is microseconds despite its name (long-standing FFmpeg quirk).
            "out_time_us", "out_time_ms" -> processedMs = (value.toLongOrNull()?.takeIf { it >= 0 } ?: 0L) / 1000
            "speed" -> speed = parseSpeed(value)
            "progress" -> if (value == "continue" || value == "end") return snapshot(done = value == "end")
        }
        return null
    }

    /** For FFmpegKit's statistics callback (time in ms, speed as a multiplier). */
    fun onStatistics(processedMs: Long, speed: Double?): MediaProcessingProgress {
        this.processedMs = processedMs.coerceAtLeast(0)
        this.speed = speed?.takeIf { it > 0 }
        return snapshot(done = false)
    }

    private fun parseSpeed(raw: String): Double? = raw.removeSuffix("x").trim().toDoubleOrNull()?.takeIf { it > 0 }

    private fun snapshot(done: Boolean): MediaProcessingProgress {
        val total = totalDurationMs
        var processed = if (done && total != null) total else processedMs
        if (total != null) processed = minOf(processed, total)
        val percentage = total?.takeIf { it > 0 }?.let { (processed.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
        val eta = total?.let {
            val remaining = it - processed
            if (remaining <= 0) 0L else speed?.let { s -> (remaining / s).toLong() }
        }
        return MediaProcessingProgress(processed, total, percentage, speed, eta)
    }

    private companion object {
        val CLASSIC_TIME = Regex("""(?:^|\s)time=(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")
        val CLASSIC_SPEED = Regex("""speed=\s*([\d.]+x?)""")
    }
}
