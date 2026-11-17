package io.bsoft.bconverter.domain.timeline

/**
 * Non-gesture ways to move around the timeline (spec §43: accessible timeline controls). Taps on a small, zoomed-out
 * timeline are hard or impossible for TalkBack, switch and keyboard users, so the playhead can be stepped and jumped
 * between clip edges, and selection can step from clip to clip.
 */
object PlayheadNavigator {
    /** Moves [playheadMs] by [deltaMs], staying inside 0..[durationMs]. */
    fun step(playheadMs: Long, deltaMs: Long, durationMs: Long): Long = (playheadMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))

    /** The nearest clip start or end strictly after [playheadMs]; null when there is none. */
    fun nextEdge(timeline: Timeline, playheadMs: Long): Long? = edges(timeline).firstOrNull { it > playheadMs }

    /** The nearest clip start or end strictly before [playheadMs]; null when already at the start. */
    fun previousEdge(timeline: Timeline, playheadMs: Long): Long? = edges(timeline).lastOrNull { it < playheadMs }

    /**
     * The clip after (+1) or before (-1) [selectedId], with the video clips first, then audio clips by start time.
     * Without a (known) selection, +1 gives the first clip and -1 the last. Null beyond either end.
     */
    fun adjacentClip(timeline: Timeline, selectedId: String?, direction: Int): String? {
        val order = timeline.videoClips.map { it.id } + timeline.audioClips.map { it.id }
        if (order.isEmpty()) return null
        val index = order.indexOf(selectedId)
        val target = when {
            index < 0 -> if (direction >= 0) 0 else order.lastIndex
            direction >= 0 -> index + 1
            else -> index - 1
        }
        return order.getOrNull(target)
    }

    private fun edges(timeline: Timeline): List<Long> =
        (timeline.videoClips.flatMap { listOf(it.startTimeMs, it.endTimeMs) } +
            timeline.audioClips.flatMap { listOf(it.startTimeMs, it.endTimeMs) }).distinct().sorted()
}
