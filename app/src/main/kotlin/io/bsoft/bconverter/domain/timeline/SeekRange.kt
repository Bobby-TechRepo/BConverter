package io.bsoft.bconverter.domain.timeline

/**
 * How far the playhead may travel. It can rest in a blank area after the last clip (shown as an empty picture) so that
 * people can look past the end, place the timeline where they want and add things there; it never plays on into it.
 */
object SeekRange {
    /** How far past the end of the content the playhead can be moved. */
    const val BLANK_TAIL_MS = 10_000L

    /** The furthest playhead position; 0 for a timeline with nothing on it. */
    fun limitMs(timeline: Timeline): Long = if (timeline.durationMs <= 0) 0L else timeline.durationMs + BLANK_TAIL_MS

    fun clamp(timeline: Timeline, positionMs: Long): Long = positionMs.coerceIn(0L, limitMs(timeline))

    /** True when [positionMs] is after the last video frame, so the preview shows nothing. The last instant itself still shows the final frame. */
    fun isBlank(timeline: Timeline, positionMs: Long): Boolean = positionMs > timeline.videoDurationMs
}
