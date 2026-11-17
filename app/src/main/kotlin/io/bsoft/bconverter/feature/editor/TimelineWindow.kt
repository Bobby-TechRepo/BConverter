package io.bsoft.bconverter.feature.editor

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The part of a [block] that is actually laid out: its intersection with the composed window. [cutDp] is how much of the
 * block's start was left off, so decorations measured from the block's real left edge can be shifted back into place.
 */
data class VisibleBlock(val block: ClipBlock, val cutDp: Float)

/**
 * Pure geometry for the virtualised timeline (implementation plan step 13). Nothing on the timeline is ever wider than the
 * composed window, however long the project or deep the zoom, so no layout node can exceed Compose's maximum size
 * (about 262,000 px on one axis) and off-screen clips are not composed at all.
 */
object TimelineWindow {
    /** How far either side of the viewport clips stay composed: enough for a fling frame, small enough to stay cheap. */
    const val COMPOSE_MARGIN_DP = 1200f

    /** Window edges move in steps of this size so scrolling recomposes once per step, not once per pixel. */
    const val STEP_DP = 100f

    /** The content position (dp, without the leading pad) at the left edge of the viewport, rounded down to a step. */
    fun windowStartDp(scrollPx: Float, density: Float, startPadDp: Float): Float =
        floor((scrollPx / density - startPadDp) / STEP_DP) * STEP_DP

    /** The visible part of [block] within [fromDp]..[toDp], or null when none of it is. */
    fun clip(block: ClipBlock, fromDp: Float, toDp: Float): VisibleBlock? {
        if (toDp <= fromDp || block.widthPx <= 0f) return null
        val start = max(block.xPx, fromDp)
        val end = min(block.xPx + block.widthPx, toDp)
        if (end <= start) return null
        return VisibleBlock(block.copy(xPx = start, widthPx = end - start), start - block.xPx)
    }

    /** Largest scroll offset (px): the whole content width minus the viewport, never negative. */
    fun maxScrollPx(contentWidthDp: Float, viewportPx: Float, density: Float): Float =
        (contentWidthDp * density - viewportPx).coerceAtLeast(0f)
}
