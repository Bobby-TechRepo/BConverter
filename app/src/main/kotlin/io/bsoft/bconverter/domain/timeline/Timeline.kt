package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.model.MediaSource
import kotlin.math.roundToLong

/** Anything placed on the timeline (spec §22). */
sealed interface TimelineItem {
    val id: String
    val startTimeMs: Long
    val durationMs: Long
}

/**
 * A slice of a source video: [sourceStartMs]..[sourceEndMs] of a source that is [sourceDurationMs] long, played at
 * [playbackSpeed]. The timeline duration is derived (span / speed) so it can never disagree with the source range.
 * Clips on the video track sit back to back; [startTimeMs] is maintained by [Timeline.normalized].
 */
data class VideoClip(
    override val id: String,
    val source: MediaSource,
    val sourceDurationMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val playbackSpeed: Float = 1f,
    override val startTimeMs: Long = 0,
) : TimelineItem {
    init {
        require(sourceStartMs >= 0) { "sourceStartMs must be >= 0" }
        require(sourceStartMs < sourceEndMs) { "source range must not be empty" }
        require(sourceEndMs <= sourceDurationMs) { "source range exceeds the source" }
        require(playbackSpeed.isFinite() && playbackSpeed in MIN_SPEED..MAX_SPEED) { "speed must be within $MIN_SPEED..$MAX_SPEED" }
        require(startTimeMs >= 0) { "startTimeMs must be >= 0" }
    }

    override val durationMs: Long
        get() = ((sourceEndMs - sourceStartMs) / playbackSpeed.toDouble()).roundToLong().coerceAtLeast(1L)
    val endTimeMs: Long get() = startTimeMs + durationMs

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}

/** A slice of a source audio file placed freely on an audio track. */
data class AudioClip(
    override val id: String,
    val source: MediaSource,
    val sourceDurationMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val volume: Float = 1f,
    override val startTimeMs: Long = 0,
) : TimelineItem {
    init {
        require(sourceStartMs >= 0) { "sourceStartMs must be >= 0" }
        require(sourceStartMs < sourceEndMs) { "source range must not be empty" }
        require(sourceEndMs <= sourceDurationMs) { "source range exceeds the source" }
        require(volume.isFinite() && volume in MIN_VOLUME..MAX_VOLUME) { "volume must be within $MIN_VOLUME..$MAX_VOLUME" }
        require(startTimeMs >= 0) { "startTimeMs must be >= 0" }
    }

    override val durationMs: Long get() = sourceEndMs - sourceStartMs
    val endTimeMs: Long get() = startTimeMs + durationMs

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
    }
}

/** One lane of audio. Clips within a lane never overlap; clips on different lanes are mixed. */
data class AudioTrack(val id: String, val clips: List<AudioClip>)

/** Immutable timeline state: one sequential video track plus any number of audio tracks. */
data class Timeline(
    val videoClips: List<VideoClip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
) {
    val videoDurationMs: Long get() = videoClips.sumOf { it.durationMs }

    /** Audio can extend beyond the video. */
    val durationMs: Long get() = maxOf(videoDurationMs, audioClips.maxOfOrNull { it.endTimeMs } ?: 0L)

    val audioClips: List<AudioClip> get() = audioTracks.flatMap { it.clips }.sortedBy { it.startTimeMs }
    val clipCount: Int get() = videoClips.size + audioTracks.sumOf { it.clips.size }

    /** Replaces every clip's source (e.g. original content URIs by staged files); nothing else changes. */
    fun mapSources(transform: (MediaSource) -> MediaSource): Timeline = copy(
        videoClips = videoClips.map { it.copy(source = transform(it.source)) },
        audioTracks = audioTracks.map { track -> track.copy(clips = track.clips.map { it.copy(source = transform(it.source)) }) },
    )

    /** Re-lays the video track so clips sit back to back from time zero. */
    fun normalized(): Timeline {
        var cursor = 0L
        return copy(videoClips = videoClips.map { clip -> clip.copy(startTimeMs = cursor).also { cursor += clip.durationMs } })
    }

    /** The video clip playing at [timeMs] (a boundary belongs to the later clip); null outside the video. */
    fun videoClipAt(timeMs: Long): VideoClip? = videoClips.firstOrNull { timeMs >= it.startTimeMs && timeMs < it.endTimeMs }

    fun findClip(id: String): TimelineItem? = videoClips.firstOrNull { it.id == id } ?: audioClips.firstOrNull { it.id == id }
    fun contains(id: String): Boolean = findClip(id) != null
}
