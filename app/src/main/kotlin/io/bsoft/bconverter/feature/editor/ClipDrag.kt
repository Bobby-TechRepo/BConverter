package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.timeline.Timeline

/** Turns a long-press drag on the timeline into the edit it means; pure, so it is tested without touch input. */
object ClipDrag {
    /** An audio clip may start anywhere from time zero on, including beyond the end of the video. */
    fun audioStart(startMs: Long, deltaMs: Long): Long = (startMs + deltaMs).coerceAtLeast(0L)

    /** The lane an audio clip is dropped on: one per [laneStepPx] dragged vertically, kept inside the existing lanes. */
    fun audioLane(lane: Int, laneCount: Int, deltaPx: Float, laneStepPx: Float): Int =
        (lane + Math.round(deltaPx / laneStepPx)).coerceIn(0, (laneCount - 1).coerceAtLeast(0))

    /**
     * The video track has no gaps, so a dragged clip takes the place its middle is nearest: its new index is the number of
     * other clips whose middle lies before the dragged clip's middle.
     */
    fun videoIndex(timeline: Timeline, clipId: String, deltaMs: Long): Int {
        val clip = timeline.videoClips.firstOrNull { it.id == clipId } ?: return 0
        val middle = clip.startTimeMs + clip.durationMs / 2.0 + deltaMs
        return timeline.videoClips.filter { it.id != clipId }.count { it.startTimeMs + it.durationMs / 2.0 < middle }
    }
}
