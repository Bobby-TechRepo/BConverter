package io.bsoft.bconverter.media.ffmpeg

/**
 * The last [maxChars] characters of a stream of log fragments (FFmpeg delivers partial lines). Thread-safe: FFmpegKit
 * calls back on its own threads. Pure, so the trimming is unit-tested.
 */
class LogTail(private val maxChars: Int = DEFAULT_MAX_CHARS) {
    init { require(maxChars > 0) }
    private val buffer = StringBuilder()

    @Synchronized fun add(fragment: String?) {
        if (fragment.isNullOrEmpty()) return
        buffer.append(fragment)
        val excess = buffer.length - maxChars
        if (excess > 0) buffer.delete(0, excess)
    }

    /** What was kept, starting at the first whole line when the front had to be cut. */
    @Synchronized fun text(): String {
        val s = buffer.toString()
        if (s.length < maxChars) return s
        val firstBreak = s.indexOf('\n')
        return if (firstBreak in 0 until s.length - 1) s.substring(firstBreak + 1) else s
    }

    companion object { const val DEFAULT_MAX_CHARS = 8 * 1024 }
}
