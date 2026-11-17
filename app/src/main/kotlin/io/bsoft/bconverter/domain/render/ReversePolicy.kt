package io.bsoft.bconverter.domain.render

/**
 * FFmpeg's `reverse` holds every frame of the clip in memory before it can play the first one. To keep that safe on a phone, a clip
 * may be reversed only while its frames (at the export's picture size, 4:2:0) fit this budget. The frames are buffered before any speed
 * change, so the limit applies to the clip's source length whatever its speed.
 */
object ReversePolicy {
    const val BUDGET_BYTES = 256L * 1024 * 1024
    private const val BYTES_PER_PIXEL = 1.5

    /** The longest clip that can be reversed for a [width] × [height] picture at [frameRate]; at least one second. */
    fun maxDurationMs(width: Int, height: Int, frameRate: Double): Long {
        if (width <= 0 || height <= 0 || frameRate <= 0.0) return MIN_MS
        val frames = BUDGET_BYTES / (width.toDouble() * height * BYTES_PER_PIXEL)
        return maxOf(MIN_MS, (frames / frameRate * 1000.0).toLong())
    }

    private const val MIN_MS = 1_000L
}
