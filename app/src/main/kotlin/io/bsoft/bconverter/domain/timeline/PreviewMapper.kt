package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.model.MediaSource
import kotlin.math.roundToLong

/** Where a timeline time lands in the video track: the clip, its index, and the matching time inside the source file. */
data class PreviewPosition(val clip: VideoClip, val clipIndex: Int, val sourcePositionMs: Long)

/** An audio clip sounding at some timeline time, with the matching time inside its source. */
data class ActiveAudio(val clip: AudioClip, val sourcePositionMs: Long)

/** One clipped, speed-adjusted piece of the video track — what a player is handed to play back (spec §26). */
data class PreviewSegment(
    val clipId: String,
    val source: MediaSource,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val speed: Float,
    val timelineStartMs: Long,
    /** The clip's own sound was detached: play it silently. */
    val muted: Boolean = false,
    /** The clip's look; the preview approximates it (see FilterPreview). */
    val filter: ClipFilter = ClipFilter.None,
    val transform: ClipTransform = ClipTransform.None,
    /** The level of the clip's own sound (the player's volume tops out at 1). */
    val volume: Float = 1f,
    /** How long the whole source file is (a composition player needs it); 0 when unknown. */
    val sourceDurationMs: Long = 0,
) {
    /** How long the segment lasts on screen. */
    val playDurationMs: Long get() = ((sourceEndMs - sourceStartMs) / speed.toDouble()).roundToLong().coerceAtLeast(1)
}

/**
 * Pure mapping between the timeline (source of truth) and what the preview player needs (spec §26, §59).
 * Nothing here renders anything; the player interprets the current project state.
 */
object PreviewMapper {
    /** The video clip playing at [timelineMs] (a boundary belongs to the later clip); null outside the video. */
    fun positionAt(timeline: Timeline, timelineMs: Long): PreviewPosition? {
        if (timelineMs < 0) return null
        val index = timeline.videoClips.indexOfLast { timelineMs >= it.startTimeMs && timelineMs < it.endTimeMs }
        if (index < 0) return null
        val clip = timeline.videoClips[index]
        val offset = ((timelineMs - clip.startTimeMs) * clip.playbackSpeed.toDouble()).roundToLong()
        return PreviewPosition(clip, index, (clip.sourceStartMs + offset).coerceIn(clip.sourceStartMs, clip.sourceEndMs))
    }

    /** Inverse of [positionAt]: the timeline time of [sourcePositionMs], clamped to the clip's own range. */
    fun timelineTimeOf(clip: VideoClip, sourcePositionMs: Long): Long {
        val inClip = sourcePositionMs.coerceIn(clip.sourceStartMs, clip.sourceEndMs) - clip.sourceStartMs
        return (clip.startTimeMs + inClip / clip.playbackSpeed.toDouble()).roundToLong().coerceIn(clip.startTimeMs, clip.endTimeMs)
    }

    /**
     * The video track as ordered playback segments. The player plays them one after another and cannot blend, so a clip whose end
     * is overlapped by a crossfade is cut where the next clip begins (the export does the real blend).
     */
    fun segments(timeline: Timeline): List<PreviewSegment> = timeline.videoClips.mapIndexed { i, clip ->
        val overlapped = timeline.overlapBefore(i + 1)
        val sourceEnd = if (overlapped > 0) {
            (clip.sourceEndMs - (overlapped * clip.playbackSpeed.toDouble()).roundToLong()).coerceAtLeast(clip.sourceStartMs + 1)
        } else clip.sourceEndMs
        if (clip.isFreeze) {
            // The player cannot hold a still picture, so a freeze is shown as a short stretch of the source in slow motion that lasts as long as the freeze.
            val window = minOf((clip.freezeMs * FreezeFrame.PREVIEW_SPEED).toLong(), clip.sourceDurationMs - clip.sourceStartMs).coerceAtLeast(1L)
            return@mapIndexed PreviewSegment(clip.id, clip.source, clip.sourceStartMs, clip.sourceStartMs + window, window.toFloat() / clip.freezeMs, clip.startTimeMs, true, clip.filter, clip.transform, 1f, clip.sourceDurationMs)
        }
        PreviewSegment(clip.id, clip.source, clip.sourceStartMs, sourceEnd, clip.playbackSpeed, clip.startTimeMs, clip.audioMuted, clip.filter, clip.transform, clip.volume, clip.sourceDurationMs)
    }

    /** Audio clips sounding at [timelineMs] (start inclusive, end exclusive), across all tracks. */
    fun activeAudioAt(timeline: Timeline, timelineMs: Long): List<ActiveAudio> =
        timeline.audioClips
            .filter { timelineMs >= it.startTimeMs && timelineMs < it.endTimeMs }
            .map { ActiveAudio(it, it.sourceStartMs + ((timelineMs - it.startTimeMs) * it.playbackSpeed.toDouble()).roundToLong()) }
}
