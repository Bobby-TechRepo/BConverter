package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
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
    /** The cut between the previous clip and this one; ignored (and reset) for the first clip. */
    val transitionIn: Transition = Transition.None,
    /** True once the clip's own sound was detached into an audio clip: the clip then plays silently. */
    val audioMuted: Boolean = false,
    /** The colour grade and effects applied to this clip only. */
    val filter: ClipFilter = ClipFilter.None,
    /** How this clip's picture is cropped, rotated and mirrored. */
    val transform: ClipTransform = ClipTransform.None,
    /** A slow zoom or pan over the clip. */
    val kenBurns: io.bsoft.bconverter.domain.transform.KenBurns = io.bsoft.bconverter.domain.transform.KenBurns.None,
    /** The level of this clip's own sound, and the fades at its ends. */
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    /** Plays the clip backwards (picture and sound). */
    val reversed: Boolean = false,
    /** Above zero, this clip is a freeze frame: the picture at [sourceStartMs], held for this long. */
    val freezeMs: Long = 0,
) : TimelineItem {
    init {
        require(sourceStartMs >= 0) { "sourceStartMs must be >= 0" }
        require(sourceStartMs < sourceEndMs) { "source range must not be empty" }
        require(sourceEndMs <= sourceDurationMs) { "source range exceeds the source" }
        require(playbackSpeed.isFinite() && playbackSpeed in MIN_SPEED..MAX_SPEED) { "speed must be within $MIN_SPEED..$MAX_SPEED" }
        require(startTimeMs >= 0) { "startTimeMs must be >= 0" }
        ClipSound(volume, fadeInMs, fadeOutMs) // the same range checks as every other clip's sound
        require(freezeMs == 0L || freezeMs in FreezeFrame.MIN_MS..FreezeFrame.MAX_MS) { "freeze length must be 0 or within ${FreezeFrame.MIN_MS}..${FreezeFrame.MAX_MS} ms" }
    }

    override val durationMs: Long
        get() = if (freezeMs > 0) freezeMs else ((sourceEndMs - sourceStartMs) / playbackSpeed.toDouble()).roundToLong().coerceAtLeast(1L)
    val isFreeze: Boolean get() = freezeMs > 0
    val endTimeMs: Long get() = startTimeMs + durationMs

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}

/** A slice of a source audio file placed freely on an audio track, played at [playbackSpeed] (pitch is kept). */
data class AudioClip(
    override val id: String,
    val source: MediaSource,
    val sourceDurationMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val volume: Float = 1f,
    override val startTimeMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
) : TimelineItem {
    init {
        require(sourceStartMs >= 0) { "sourceStartMs must be >= 0" }
        require(sourceStartMs < sourceEndMs) { "source range must not be empty" }
        require(sourceEndMs <= sourceDurationMs) { "source range exceeds the source" }
        require(playbackSpeed.isFinite() && playbackSpeed in VideoClip.MIN_SPEED..VideoClip.MAX_SPEED) { "speed must be within ${VideoClip.MIN_SPEED}..${VideoClip.MAX_SPEED}" }
        require(volume.isFinite() && volume in MIN_VOLUME..MAX_VOLUME) { "volume must be within $MIN_VOLUME..$MAX_VOLUME" }
        require(startTimeMs >= 0) { "startTimeMs must be >= 0" }
        ClipSound(volume, fadeInMs, fadeOutMs)
    }

    override val durationMs: Long get() = ((sourceEndMs - sourceStartMs) / playbackSpeed.toDouble()).roundToLong().coerceAtLeast(1L)
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
    /** Videos shown over the main video; later ones are on top. */
    val pipClips: List<PipClip> = emptyList(),
) {
    /** The length of the video track: the clips back to back, less what crossfades overlap. */
    val videoDurationMs: Long get() = videoClips.sumOf { it.durationMs } - videoClips.indices.sumOf { overlapBefore(it) }

    /** What is really applied at the cut before the clip at [index], shortened if either clip is too short for it. */
    fun joinBefore(index: Int): JoinEffect {
        if (index <= 0 || index >= videoClips.size) return JoinEffect.None
        val clip = videoClips[index]
        val previous = videoClips[index - 1]
        val transition = clip.transitionIn
        val shorter = minOf(previous.durationMs, clip.durationMs)
        val length = when (transition.type) {
            TransitionType.NONE -> 0L
            TransitionType.FADE_BLACK -> minOf(transition.durationMs, shorter)
            TransitionType.CROSSFADE -> minOf(transition.durationMs, shorter / 2)
        }
        return if (length <= 0) JoinEffect.None else JoinEffect(transition.type, length)
    }

    fun overlapBefore(index: Int): Long = joinBefore(index).overlapMs

    /** Audio can extend beyond the video. */
    val durationMs: Long get() = maxOf(videoDurationMs, audioClips.maxOfOrNull { it.endTimeMs } ?: 0L)

    val audioClips: List<AudioClip> get() = audioTracks.flatMap { it.clips }.sortedBy { it.startTimeMs }
    val clipCount: Int get() = videoClips.size + audioTracks.sumOf { it.clips.size } + pipClips.size

    /** Replaces every clip's source (e.g. original content URIs by staged files); nothing else changes. */
    fun mapSources(transform: (MediaSource) -> MediaSource): Timeline = copy(
        videoClips = videoClips.map { it.copy(source = transform(it.source)) },
        audioTracks = audioTracks.map { track -> track.copy(clips = track.clips.map { it.copy(source = transform(it.source)) }) },
        pipClips = pipClips.map { it.copy(source = transform(it.source)) },
    )

    /** Re-lays the video track so clips sit back to back from time zero (a crossfade pulls the next clip back over the previous one). */
    fun normalized(): Timeline {
        val clean = copy(videoClips = videoClips.mapIndexed { i, c -> if (i == 0 && !c.transitionIn.isNone) c.copy(transitionIn = Transition.None) else c })
        var cursor = 0L
        return clean.copy(
            videoClips = clean.videoClips.mapIndexed { i, clip ->
                val start = cursor - clean.overlapBefore(i)
                cursor = start + clip.durationMs
                clip.copy(startTimeMs = start)
            },
        )
    }

    /** The video clip playing at [timeMs] (a boundary belongs to the later clip, and so does an overlap); null outside the video. */
    fun videoClipAt(timeMs: Long): VideoClip? = videoClips.lastOrNull { timeMs >= it.startTimeMs && timeMs < it.endTimeMs }

    fun findClip(id: String): TimelineItem? = videoClips.firstOrNull { it.id == id } ?: audioClips.firstOrNull { it.id == id }
    fun contains(id: String): Boolean = findClip(id) != null || findPip(id) != null
    fun findPip(id: String): PipClip? = pipClips.firstOrNull { it.id == id }
}
