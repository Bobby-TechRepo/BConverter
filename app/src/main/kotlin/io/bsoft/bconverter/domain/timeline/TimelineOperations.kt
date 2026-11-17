package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
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
                if (c.isFreeze) return fail(EditError.NotForFreezeFrame)
                if (positionMs <= c.startTimeMs || positionMs >= c.endTimeMs) return fail(EditError.SplitOutsideClip)
                val sourceOffset = ((positionMs - c.startTimeMs) * c.playbackSpeed.toDouble()).roundToLong()
                // A reversed clip plays its source from the end, so the first half on the timeline is the later part of the source.
                val splitAt = if (c.reversed) c.sourceEndMs - sourceOffset else c.sourceStartMs + sourceOffset
                if (splitAt <= c.sourceStartMs || splitAt >= c.sourceEndMs) return fail(EditError.SplitOutsideClip)
                val clips = t.videoClips.toMutableList()
                if (c.reversed) {
                    clips[loc.index] = c.copy(sourceStartMs = splitAt, fadeOutMs = 0)
                    clips.add(loc.index + 1, c.copy(id = newClipId, sourceEndMs = splitAt, transitionIn = Transition.None, fadeInMs = 0))
                    return ok(t.copy(videoClips = clips))
                }
                clips[loc.index] = c.copy(sourceEndMs = splitAt, fadeOutMs = 0) // the fade-out belongs to the second half
                // The new second half starts at the cut, which is a plain one: the transition stays with the first half.
                clips.add(loc.index + 1, c.copy(id = newClipId, sourceStartMs = splitAt, transitionIn = Transition.None, fadeInMs = 0))
                ok(t.copy(videoClips = clips))
            }
            is Located.Audio -> {
                val c = loc.clip
                if (positionMs <= c.startTimeMs || positionMs >= c.endTimeMs) return fail(EditError.SplitOutsideClip)
                val splitAt = c.sourceStartMs + ((positionMs - c.startTimeMs) * c.playbackSpeed.toDouble()).roundToLong()
                if (splitAt <= c.sourceStartMs || splitAt >= c.sourceEndMs) return fail(EditError.SplitOutsideClip)
                val first = c.copy(sourceEndMs = splitAt, fadeOutMs = 0)
                val second = c.copy(id = newClipId, sourceStartMs = splitAt, startTimeMs = positionMs, fadeInMs = 0)
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
        if (loc is Located.Video && loc.clip.isFreeze) return fail(EditError.NotForFreezeFrame)
        val sourceDuration = when (loc) { is Located.Video -> loc.clip.sourceDurationMs; is Located.Audio -> loc.clip.sourceDurationMs }
        if (startMs < 0 || startMs >= endMs || endMs > sourceDuration) return fail(EditError.InvalidTrimRange)
        return when (loc) {
            is Located.Video -> withVideo(t, loc.index, loc.clip.copy(sourceStartMs = startMs, sourceEndMs = endMs))
            is Located.Audio -> withAudio(t, loc.trackIndex, loc.clip, loc.clip.copy(sourceStartMs = startMs, sourceEndMs = endMs))
        }
    }

    // ---------------- remove ----------------
    fun remove(t: Timeline, clipId: String): EditResult {
        if (t.findPip(clipId) != null) return ok(t.copy(pipClips = t.pipClips.filter { it.id != clipId }))
        locate(t, clipId) ?: return notFound(clipId)
        return ok(
            t.copy(
                videoClips = t.videoClips.filter { it.id != clipId },
                audioTracks = t.audioTracks.map { it.copy(clips = it.clips.filter { c -> c.id != clipId }) }.filter { it.clips.isNotEmpty() },
            ),
        )
    }

    // ---------------- picture in picture ----------------
    fun addPip(t: Timeline, clip: PipClip): EditResult {
        if (t.contains(clip.id)) return fail(EditError.DuplicateId)
        return ok(t.copy(pipClips = t.pipClips + clip))
    }

    private fun pipOrNull(t: Timeline, id: String) = t.findPip(id)
    private fun withPip(t: Timeline, clip: PipClip): EditResult = ok(t.copy(pipClips = t.pipClips.map { if (it.id == clip.id) clip else it }))

    fun movePip(t: Timeline, clipId: String, startMs: Long): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        if (startMs < 0) return fail(EditError.InvalidStartTime)
        return withPip(t, c.copy(startTimeMs = startMs))
    }

    fun trimPip(t: Timeline, clipId: String, startMs: Long, endMs: Long): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        if (startMs < 0 || endMs <= startMs || endMs > c.sourceDurationMs) return fail(EditError.InvalidTrimRange)
        val trimmed = c.copy(sourceStartMs = startMs, sourceEndMs = endMs)
        return withPip(t, trimmed.copy(keyframes = PipKeyframes.fitted(c.keyframes, trimmed.durationMs)))
    }

    fun setPipLayout(t: Timeline, clipId: String, layout: PipLayout): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        // A clip that glides is moved as a whole: the end point follows the start point.
        val motion = c.motion?.let { m ->
            m.copy(endX = (m.endX + layout.centerX - c.layout.centerX).coerceIn(0f, 1f), endY = (m.endY + layout.centerY - c.layout.centerY).coerceIn(0f, 1f))
        }
        return withPip(t, c.copy(layout = layout, motion = motion))
    }

    fun setPipVolume(t: Timeline, clipId: String, volume: Float): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        if (!volume.isFinite() || volume < 0f || volume > 2f) return fail(EditError.InvalidVolume)
        return withPip(t, c.copy(volume = volume))
    }

    fun setPipMotion(t: Timeline, clipId: String, motion: PipMotion?): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        return withPip(t, c.copy(motion = motion))
    }

    fun setPipSpeed(t: Timeline, clipId: String, speed: Float): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        if (!speed.isFinite() || speed < VideoClip.MIN_SPEED || speed > VideoClip.MAX_SPEED) return fail(EditError.InvalidSpeed)
        val resped = c.copy(speed = speed)
        return withPip(t, resped.copy(keyframes = PipKeyframes.fitted(c.keyframes, resped.durationMs)))
    }

    fun setPipShape(t: Timeline, clipId: String, shape: PipShape): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        return withPip(t, c.copy(shape = shape))
    }

    fun setPipKeyframes(t: Timeline, clipId: String, keyframes: List<PipKeyframe>): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        if (!PipKeyframes.isValid(keyframes, c.durationMs)) return fail(EditError.InvalidKeyframes)
        // Keyframes replace a glide.
        return withPip(t, c.copy(keyframes = keyframes, motion = if (keyframes.isNotEmpty()) null else c.motion))
    }

    fun setPipChromaKey(t: Timeline, clipId: String, key: ChromaKey?): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        return withPip(t, c.copy(chromaKey = key))
    }

    fun setPipFades(t: Timeline, clipId: String, fadeInMs: Long, fadeOutMs: Long): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        val max = io.bsoft.bconverter.domain.audio.ClipSound.MAX_FADE_MS
        if (fadeInMs !in 0..max || fadeOutMs !in 0..max) return fail(EditError.InvalidFade)
        return withPip(t, c.copy(fadeInMs = fadeInMs, fadeOutMs = fadeOutMs))
    }

    /** Puts a picture-in-picture clip on top of the others (the last one is drawn last). */
    fun bringPipToFront(t: Timeline, clipId: String): EditResult {
        val c = pipOrNull(t, clipId) ?: return notFound(clipId)
        return ok(t.copy(pipClips = t.pipClips.filter { it.id != clipId } + c))
    }

    // ---------------- speed / volume ----------------
    fun changeSpeed(t: Timeline, clipId: String, speed: Float): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc is Located.Video && loc.clip.isFreeze) return fail(EditError.NotForFreezeFrame)
        if (!speed.isFinite() || speed < VideoClip.MIN_SPEED || speed > VideoClip.MAX_SPEED) return fail(EditError.InvalidSpeed)
        return when (loc) {
            is Located.Video -> withVideo(t, loc.index, loc.clip.copy(playbackSpeed = speed))
            is Located.Audio -> withAudio(t, loc.trackIndex, loc.clip, loc.clip.copy(playbackSpeed = speed))
        }
    }

    /** Sets the look of one video clip; the preset name travels with the filter. */
    fun setFilter(t: Timeline, clipId: String, filter: ClipFilter): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        return withVideo(t, loc.index, loc.clip.copy(filter = filter))
    }

    /** Gives every video clip the same look. */
    fun setFilterForAll(t: Timeline, filter: ClipFilter): EditResult {
        if (t.videoClips.isEmpty()) return fail(EditError.NothingSelected)
        return ok(t.copy(videoClips = t.videoClips.map { it.copy(filter = filter) }))
    }

    /** Sets how one video clip is cropped, rotated and mirrored. */
    fun setTransform(t: Timeline, clipId: String, transform: ClipTransform): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        return withVideo(t, loc.index, loc.clip.copy(transform = transform))
    }

    /** Sets the slow zoom or pan of one video clip; a freeze frame has no motion to give. */
    fun setKenBurns(t: Timeline, clipId: String, kenBurns: io.bsoft.bconverter.domain.transform.KenBurns): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        if (loc.clip.isFreeze) return fail(EditError.NotForFreezeFrame)
        return withVideo(t, loc.index, loc.clip.copy(kenBurns = kenBurns))
    }

    /** Gives every video clip (freeze frames excepted) the same slow zoom or pan. */
    fun setKenBurnsForAll(t: Timeline, kenBurns: io.bsoft.bconverter.domain.transform.KenBurns): EditResult {
        if (t.videoClips.isEmpty()) return fail(EditError.NothingSelected)
        return ok(t.copy(videoClips = t.videoClips.map { if (it.isFreeze) it else it.copy(kenBurns = kenBurns) }))
    }

    /** Gives every video clip the same crop, rotation and mirroring. */
    fun setTransformForAll(t: Timeline, transform: ClipTransform): EditResult {
        if (t.videoClips.isEmpty()) return fail(EditError.NothingSelected)
        return ok(t.copy(videoClips = t.videoClips.map { it.copy(transform = transform) }))
    }

    /**
     * Holds the picture at [positionMs] for [freezeMs]: the clip is split there and a freeze clip is put between the halves.
     * The freeze keeps the clip's look and framing; it has no sound.
     */
    fun insertFreeze(t: Timeline, clipId: String, positionMs: Long, freezeId: String, secondHalfId: String, freezeMs: Long): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        val c = loc.clip
        if (c.isFreeze || c.reversed) return fail(EditError.NotForFreezeFrame)
        if (freezeMs !in FreezeFrame.MIN_MS..FreezeFrame.MAX_MS) return fail(EditError.InvalidTransition)
        if (freezeId == secondHalfId || t.contains(freezeId) || t.contains(secondHalfId)) return fail(EditError.DuplicateId)
        if (positionMs <= c.startTimeMs || positionMs >= c.endTimeMs) return fail(EditError.SplitOutsideClip)
        val splitAt = c.sourceStartMs + ((positionMs - c.startTimeMs) * c.playbackSpeed.toDouble()).roundToLong()
        if (splitAt <= c.sourceStartMs || splitAt >= c.sourceEndMs) return fail(EditError.SplitOutsideClip)
        // Stay a little inside the clip so the export can always read a picture there.
        val frameAt = splitAt.coerceAtMost(c.sourceEndMs - FreezeFrame.INPUT_WINDOW_MS / 2).coerceAtLeast(c.sourceStartMs)
        val freeze = c.copy(
            id = freezeId, sourceStartMs = frameAt, sourceEndMs = minOf(frameAt + FreezeFrame.INPUT_WINDOW_MS, c.sourceDurationMs),
            playbackSpeed = 1f, transitionIn = Transition.None, audioMuted = true, volume = 1f, fadeInMs = 0, fadeOutMs = 0, freezeMs = freezeMs,
        )
        val first = c.copy(sourceEndMs = splitAt, fadeOutMs = 0)
        val second = c.copy(id = secondHalfId, sourceStartMs = splitAt, transitionIn = Transition.None, fadeInMs = 0)
        val clips = t.videoClips.toMutableList()
        clips[loc.index] = first
        clips.add(loc.index + 1, freeze)
        clips.add(loc.index + 2, second)
        return ok(t.copy(videoClips = clips))
    }

    /** Changes how long a freeze frame is held. */
    fun setFreezeLength(t: Timeline, clipId: String, freezeMs: Long): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video || !loc.clip.isFreeze) return fail(EditError.WrongClipType)
        if (freezeMs !in FreezeFrame.MIN_MS..FreezeFrame.MAX_MS) return fail(EditError.InvalidTransition)
        return withVideo(t, loc.index, loc.clip.copy(freezeMs = freezeMs))
    }

    /** Plays a video clip backwards, or forwards again. */
    fun setReversed(t: Timeline, clipId: String, reversed: Boolean): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        if (loc.clip.isFreeze) return fail(EditError.NotForFreezeFrame)
        return withVideo(t, loc.index, loc.clip.copy(reversed = reversed))
    }

    /** Sets the level and fades of a video clip's own sound or of an audio clip. */
    fun setSound(t: Timeline, clipId: String, sound: ClipSound): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        return when (loc) {
            is Located.Video -> withVideo(t, loc.index, loc.clip.copy(volume = sound.volume, fadeInMs = sound.fadeInMs, fadeOutMs = sound.fadeOutMs))
            is Located.Audio -> withAudio(t, loc.trackIndex, loc.clip, loc.clip.copy(volume = sound.volume, fadeInMs = sound.fadeInMs, fadeOutMs = sound.fadeOutMs))
        }
    }

    fun changeVolume(t: Timeline, clipId: String, volume: Float): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Audio) return fail(EditError.WrongClipType)
        if (!volume.isFinite() || volume < AudioClip.MIN_VOLUME || volume > AudioClip.MAX_VOLUME) return fail(EditError.InvalidVolume)
        return withAudio(t, loc.trackIndex, loc.clip, loc.clip.copy(volume = volume))
    }

    // ---------------- detach audio ----------------
    /** Mutes video clip [clipId] and puts its sound on the audio lanes as [newClipId], same source slice, same time. */
    fun detachAudio(t: Timeline, clipId: String, newClipId: String, newTrackId: String): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        val c = loc.clip
        if (c.audioMuted) return fail(EditError.AlreadyDetached)
        if (t.contains(newClipId)) return fail(EditError.DuplicateId)
        val muted = t.copy(videoClips = t.videoClips.toMutableList().also { it[loc.index] = c.copy(audioMuted = true) })
        val audio = AudioClip(newClipId, c.source, c.sourceDurationMs, c.sourceStartMs, c.sourceEndMs, 1f, c.startTimeMs, c.playbackSpeed)
        return insertAudio(muted, audio, c.startTimeMs, newTrackId)
    }

    // ---------------- transitions ----------------
    /** Sets the transition on the cut before the video clip [clipId]; [Transition.None] removes it. */
    fun setTransition(t: Timeline, clipId: String, transition: Transition): EditResult {
        val loc = locate(t, clipId) ?: return notFound(clipId)
        if (loc !is Located.Video) return fail(EditError.WrongClipType)
        if (loc.index == 0) return fail(EditError.NoPreviousClip)
        return withVideo(t, loc.index, loc.clip.copy(transitionIn = transition))
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
