package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.EditResult.Failure
import io.bsoft.bconverter.domain.timeline.EditResult.Success
import kotlin.math.roundToLong

/**
 * Pure editing operations: each takes a timeline and returns a new one (or an [EditError]); nothing is mutated.
 * Video positions are timeline times in ms; trim ranges are source times in ms. The video track ripples
 * (stays gap-free) after every change.
 */
object TimelineOperations {
    private sealed interface Located {
        data class Video(val index: Int, val clip: VideoClip) : Located
        data class Audio(val trackIndex: Int, val clip: AudioClip) : Located
    }

    private fun locate(t: Timeline, id: String): Located? {
        t.videoClips.forEachIndexed { i, c -> if (c.id == id) return Located.Video(i, c) }
        t.audioTracks.forEachIndexed { ti, track -> track.clips.firstOrNull { it.id == id }?.let { return Located.Audio(ti, it) } }
        return null
    }

    private fun ok(t: Timeline): EditResult = Success(t.normalized())
    private fun fail(e: EditError): EditResult = Failure(e)
    private fun notFound(id: String): EditResult = fail(EditError.ClipNotFound(id))
    private fun overlaps(a: AudioClip, b: AudioClip) = a.startTimeMs < b.endTimeMs && b.startTimeMs < a.endTimeMs

    private fun withVideo(t: Timeline, index: Int, clip: VideoClip): EditResult =
        ok(t.copy(videoClips = t.videoClips.toMutableList().also { it[index] = clip }))

    /** Replaces [old] with [updated] in its track, refusing if the result would overlap a neighbour. */
    private fun withAudio(t: Timeline, trackIndex: Int, old: AudioClip, updated: AudioClip): EditResult {
        val track = t.audioTracks[trackIndex]
        val others = track.clips.filter { it.id != old.id }
        if (others.any { overlaps(it, updated) }) return fail(EditError.AudioOverlap)
        val tracks = t.audioTracks.toMutableList()
        tracks[trackIndex] = track.copy(clips = (others + updated).sortedBy { it.startTimeMs })
        return ok(t.copy(audioTracks = tracks))
    }

    // ---------------- split ----------------
    /** Splits the clip at timeline position [positionMs] (strictly inside the clip); the second half gets [newClipId]. */
    fun split(t: Timeline, clipId: String, positionMs: Long, newClipId: String): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (t.contains(newClipId)) return fail(EditError.DuplicateId)
        return when (loc) {
            is Located.Video -> {
                val c = loc.clip
                if (positionMs <= c.startTimeMs || positionMs >= c.endTimeMs) return fail(EditError.SplitOutsideClip)
                val sourceOffset = ((positionMs - c.startTimeMs) * c.playbackSpeed.toDouble()).roundToLong()
                val splitAt = c.sourceStartMs + sourceOffset
                if (splitAt <= c.sourceStartMs || splitAt >= c.sourceEndMs) return fail(EditError.SplitOutsideClip)
                val clips = t.videoClips.toMutableList()
                clips[loc.index] = c.copy(sourceEndMs = splitAt)
                clips.add(loc.index + 1, c.copy(id = newClipId, sourceStartMs = splitAt))
                ok(t.copy(videoClips = clips))
            }
            is Located.Audio -> {
                val c = loc.clip
                if (positionMs <= c.startTimeMs || positionMs >= c.endTimeMs) return fail(EditError.SplitOutsideClip)
                val splitAt = c.sourceStartMs + (positionMs - c.startTimeMs)
                val first = c.copy(sourceEndMs = splitAt)
                val second = c.copy(id = newClipId, sourceStartMs = splitAt, startTimeMs = positionMs)
                val track = t.audioTracks[loc.trackIndex]
                val tracks = t.audioTracks.toMutableList()
                tracks[loc.trackIndex] = track.copy(clips = (track.clips.filter { it.id != clipId } + first + second).sortedBy { it.startTimeMs })
                ok(t.copy(audioTracks = tracks))
            }
        }
    }

    // ---------------- trim ----------------
    /** Sets the clip's source range; valid when 0 <= [startMs] < [endMs] <= the source duration. */
    fun trim(t: Timeline, clipId: String, startMs: Long, endMs: Long): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        val sourceDuration = when (loc) { is Located.Video -> loc.clip.sourceDurationMs; is Located.Audio -> loc.clip.sourceDurationMs }
        if (startMs < 0 || startMs >= endMs || endMs > sourceDuration) return fail(EditError.InvalidTrimRange)
        return when (loc) {
            is Located.Video -> withVideo(t, loc.index, loc.clip.copy(sourceStartMs = startMs, sourceEndMs = endMs))
            is Located.Audio -> withAudio(t, loc.trackIndex, loc.clip, loc.clip.copy(sourceStartMs = startMs, sourceEndMs = endMs))
        }
    }

    // ---------------- remove ----------------
    fun remove(t: Timeline, clipId: String): EditResult {
        locate(t, clipId) ?: return notFound(clipId)
        return ok(
            t.copy(
                videoClips = t.videoClips.filter { it.id != clipId },
                audioTracks = t.audioTracks.map { it.copy(clips = it.clips.filter { c -> c.id != clipId }) }.filter { it.clips.isNotEmpty() },
            ),
        )
    }

    // ---------------- speed / volume ----------------
    fun changeSpeed(t: Timeline, clipId: String, speed: Float): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        if (!speed.isFinite() || speed < VideoClip.MIN_SPEED || speed > VideoClip.MAX_SPEED) return fail(EditError.InvalidSpeed)
        return withVideo(t, loc.index, loc.clip.copy(playbackSpeed = speed))
    }

    fun changeVolume(t: Timeline, clipId: String, volume: Float): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Audio) return fail(EditError.WrongClipType)
        if (!volume.isFinite() || volume < AudioClip.MIN_VOLUME || volume > AudioClip.MAX_VOLUME) return fail(EditError.InvalidVolume)
        return withAudio(t, loc.trackIndex, loc.clip, loc.clip.copy(volume = volume))
    }

    // ---------------- video insert / move ----------------
    fun insertVideo(t: Timeline, clip: VideoClip, index: Int): EditResult {
        if (t.contains(clip.id)) return fail(EditError.DuplicateId)
        if (index !in 0..t.videoClips.size) return fail(EditError.InvalidIndex)
        return ok(t.copy(videoClips = t.videoClips.toMutableList().also { it.add(index, clip) }))
    }

    /** Moves a video clip to position [toIndex] in the video track. */
    fun moveVideo(t: Timeline, clipId: String, toIndex: Int): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        if (toIndex !in t.videoClips.indices) return fail(EditError.InvalidIndex)
        val clips = t.videoClips.toMutableList()
        clips.removeAt(loc.index)
        clips.add(toIndex, loc.clip)
        return ok(t.copy(videoClips = clips))
    }

    // ---------------- audio insert / move ----------------
    /** Places [clip] at [startMs] on the first track with room, creating track [newTrackId] if every track is busy. */
    fun insertAudio(t: Timeline, clip: AudioClip, startMs: Long, newTrackId: String): EditResult {
        if (startMs < 0) return fail(EditError.InvalidStartTime)
        if (t.contains(clip.id)) return fail(EditError.DuplicateId)
        val placed = clip.copy(startTimeMs = startMs)
        val lane = t.audioTracks.indexOfFirst { track -> track.clips.none { overlaps(it, placed) } }
        val tracks = if (lane >= 0) {
            t.audioTracks.mapIndexed { i, track -> if (i == lane) track.copy(clips = (track.clips + placed).sortedBy { it.startTimeMs }) else track }
        } else {
            t.audioTracks + AudioTrack(newTrackId, listOf(placed))
        }
        return ok(t.copy(audioTracks = tracks))
    }

    /** Moves an audio clip in time, optionally onto another track ([trackIndex]); emptied tracks disappear. */
    fun moveAudio(t: Timeline, clipId: String, startMs: Long, trackIndex: Int? = null): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Audio) return fail(EditError.WrongClipType)
        if (startMs < 0) return fail(EditError.InvalidStartTime)
        val target = trackIndex ?: loc.trackIndex
        if (target !in t.audioTracks.indices) return fail(EditError.InvalidIndex)
        val moved = loc.clip.copy(startTimeMs = startMs)
        val others = t.audioTracks[target].clips.filter { it.id != clipId }
        if (others.any { overlaps(it, moved) }) return fail(EditError.AudioOverlap)
        val tracks = t.audioTracks.mapIndexed { i, track ->
            when (i) {
                target -> track.copy(clips = (others + moved).sortedBy { it.startTimeMs })
                loc.trackIndex -> track.copy(clips = track.clips.filter { it.id != clipId })
                else -> track
            }
        }.filter { it.clips.isNotEmpty() }
        return ok(t.copy(audioTracks = tracks))
    }

    // ---------------- replace ----------------
    /** Swaps the media of a clip, keeping its id, place, speed/volume; the range becomes the whole new source. */
    fun replace(t: Timeline, clipId: String, source: MediaSource, sourceDurationMs: Long): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (sourceDurationMs <= 0) return fail(EditError.InvalidSourceDuration)
        return when (loc) {
            is Located.Video -> withVideo(
                t, loc.index,
                loc.clip.copy(source = source, sourceDurationMs = sourceDurationMs, sourceStartMs = 0, sourceEndMs = sourceDurationMs),
            )
            is Located.Audio -> withAudio(
                t, loc.trackIndex, loc.clip,
                loc.clip.copy(source = source, sourceDurationMs = sourceDurationMs, sourceStartMs = 0, sourceEndMs = sourceDurationMs),
            )
        }
    }
}
