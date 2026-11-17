package io.bsoft.bconverter.domain.overlay

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure size rules for pictures put on the video, so the Android importer only does the decoding. */
object OverlayImageSizing {
    /** The longest side kept when a picture is imported; larger pictures are shrunk (never enlarged). */
    const val MAX_SIDE = 2048
    private const val MAX_SOURCE_PIXELS = 100L * 1000 * 1000
    private const val STANDARD_HEIGHT = 0.3f
    private const val MAX_WIDTH_SHARE = 0.5f

    /** The size after shrinking to [maxSide] on the longest side, keeping the shape; each side is at least 1. */
    fun fit(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val k = maxSide.toDouble() / longest
        return (width * k).roundToInt().coerceAtLeast(1) to (height * k).roundToInt().coerceAtLeast(1)
    }

    /** The decoder's power-of-two sample size: as large as possible while the result is still at least [maxSide] on the longest side. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    fun isAcceptable(width: Int, height: Int): Boolean = width > 0 && height > 0 && width.toLong() * height <= MAX_SOURCE_PIXELS

    /** A third of the frame height, but never wider than half the frame: a banner would otherwise cover the picture. */
    fun defaultHeightFraction(aspect: Float, frameAspect: Float): Float {
        if (!aspect.isFinite() || aspect <= 0f || !frameAspect.isFinite() || frameAspect <= 0f) return STANDARD_HEIGHT
        return minOf(STANDARD_HEIGHT, MAX_WIDTH_SHARE * frameAspect / aspect).coerceAtLeast(0.01f)
    }
}
