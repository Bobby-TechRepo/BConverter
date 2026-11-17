package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Horizontal scroll position of the virtualised timeline, in px. Unlike `ScrollState` (which needs `horizontalScroll`
 * and therefore one child as wide as the whole timeline) its range is set by the timeline itself ([maxValue]); content is
 * positioned from [value] in the layout phase, so scrolling moves things without recomposing them.
 */
@Stable
class TimelineScrollState(initial: Float = 0f) : ScrollableState {
    var value: Float by mutableFloatStateOf(initial)
        private set

    /** Set by the timeline from its content width; the position is pulled back inside when it shrinks. */
    var maxValue: Float by mutableFloatStateOf(Float.MAX_VALUE)
        internal set

    private val delegate = ScrollableState { delta ->
        val target = (value + delta).coerceIn(0f, maxValue)
        val consumed = target - value
        value = target
        consumed
    }

    override val isScrollInProgress: Boolean get() = delegate.isScrollInProgress
    override fun dispatchRawDelta(delta: Float): Float = delegate.dispatchRawDelta(delta)
    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) = delegate.scroll(scrollPriority, block)

    /** Jumps to [target] (clamped), as a scroll of its own so it cancels a running fling. */
    suspend fun scrollTo(target: Float) = scroll { scrollBy(target.coerceIn(0f, maxValue) - value) }

    internal fun updateMax(max: Float) {
        maxValue = max.coerceAtLeast(0f)
        if (value > maxValue) value = maxValue
    }
}
