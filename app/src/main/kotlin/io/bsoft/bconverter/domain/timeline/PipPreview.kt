package io.bsoft.bconverter.domain.timeline

/** A picture-in-picture clip that is showing at some moment, and where in its source file that moment is. */
data class ActivePip(val clip: PipClip, val sourcePositionMs: Long)

/** What the preview needs to know about picture-in-picture clips: which ones show now, and how a finger moves one. */
object PipPreview {
    /** The clips showing at [timelineMs] (start inclusive, end exclusive), back to front (the order of [Timeline.pipClips]). */
    fun activeAt(pips: List<PipClip>, timelineMs: Long): List<ActivePip> =
        pips.filter { timelineMs >= it.startTimeMs && timelineMs < it.endTimeMs }
            .map { ActivePip(it, it.sourceStartMs + Math.round((timelineMs - it.startTimeMs) * it.speed.toDouble()).coerceAtMost(it.sourceEndMs - it.sourceStartMs)) }

    /**
     * The layout after a drag and pinch: [dxFraction]/[dyFraction] are the finger movement as shares of the picture's width and
     * height, [zoom] multiplies the width. The centre stays inside the frame and the width inside its limits.
     */
    fun transformed(layout: PipLayout, dxFraction: Float, dyFraction: Float, zoom: Float): PipLayout {
        if (!(dxFraction.isFinite() && dyFraction.isFinite() && zoom.isFinite()) || zoom <= 0f) return layout
        return layout.copy(
            centerX = (layout.centerX + dxFraction).coerceIn(0f, 1f),
            centerY = (layout.centerY + dyFraction).coerceIn(0f, 1f),
            width = (layout.width * zoom).coerceIn(PipLayout.MIN_WIDTH, PipLayout.MAX_WIDTH),
        )
    }

    /** Snaps a centre coordinate to the middle (0.5) when within [threshold] of it. */
    fun snapped(value: Float, threshold: Float = 0.02f): Float = if (kotlin.math.abs(value - 0.5f) <= threshold) 0.5f else value
}
