package io.bsoft.bconverter.domain.timeline

import kotlin.math.roundToLong

/**
 * Step 20: a composition player has one clock for the whole video track, while the preview controller talks in
 * "segment N at source position P". This converts between the two; segments play back to back, each for its
 * [PreviewSegment.playDurationMs].
 */
class CompositionTimeMap(private val segments: List<PreviewSegment>) {
    private val starts: LongArray = LongArray(segments.size).also { s ->
        var cursor = 0L
        segments.forEachIndexed { i, seg -> s[i] = cursor; cursor += seg.playDurationMs }
    }

    val totalMs: Long get() = if (segments.isEmpty()) 0L else starts.last() + segments.last().playDurationMs

    /** Composition time of [sourcePositionMs] in segment [index] (clamped to the segment). */
    fun toCompositionMs(index: Int, sourcePositionMs: Long): Long {
        val seg = segments.getOrNull(index) ?: return 0L
        val inSource = sourcePositionMs.coerceIn(seg.sourceStartMs, seg.sourceEndMs) - seg.sourceStartMs
        return starts[index] + (inSource / seg.speed.toDouble()).roundToLong().coerceAtMost(seg.playDurationMs)
    }

    /** The segment and source position playing at composition time [ms] (a boundary belongs to the later segment). */
    fun fromCompositionMs(ms: Long): Pair<Int, Long>? {
        if (segments.isEmpty()) return null
        val t = ms.coerceIn(0, totalMs)
        var index = starts.indexOfLast { it <= t }.coerceAtLeast(0)
        if (index == segments.lastIndex && t >= totalMs) index = segments.lastIndex
        val seg = segments[index]
        val source = seg.sourceStartMs + ((t - starts[index]) * seg.speed.toDouble()).roundToLong()
        return index to source.coerceIn(seg.sourceStartMs, seg.sourceEndMs)
    }
}
