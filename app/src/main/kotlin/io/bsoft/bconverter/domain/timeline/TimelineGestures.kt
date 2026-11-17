package io.bsoft.bconverter.domain.timeline

import kotlin.math.abs
import kotlin.math.roundToLong

/** Which end of a clip a trim handle moves (in timeline order: [START] is the left edge). */
enum class TrimEdge { START, END }

/**
 * Step 24: trimming a clip by dragging its edge on the timeline. The drag is a timeline distance; it becomes a source
 * change at the clip's speed (a reversed clip plays its source backwards, so its left edge is its source end). Moving
 * the left edge of an audio or picture-in-picture clip also moves its start, so the picture or sound under the edge
 * stays where it was; the video track closes up by itself.
 */
object EdgeTrim {
    /** The shortest a clip can be trimmed to on the timeline. */
    const val MIN_CLIP_MS = 100L

    private class Edges(
        val speed: Double, val sourceStart: Long, val sourceEnd: Long, val sourceDuration: Long,
        val reversed: Boolean,
        /** Timeline start for clips that keep their place (audio, picture-in-picture); null for the main video track. */
        val placedAt: Long?,
        /** How far the clip may grow left / right before touching a neighbour on its lane (audio only). */
        val roomBefore: Long = Long.MAX_VALUE, val roomAfter: Long = Long.MAX_VALUE,
    )

    private fun edgesOf(t: Timeline, clipId: String): Edges? {
        t.videoClips.firstOrNull { it.id == clipId }?.let { c ->
            if (c.isFreeze) return null
            return Edges(c.playbackSpeed.toDouble(), c.sourceStartMs, c.sourceEndMs, c.sourceDurationMs, c.reversed, null)
        }
        t.audioTracks.forEach { track ->
            val c = track.clips.firstOrNull { it.id == clipId } ?: return@forEach
            val before = track.clips.filter { it.id != c.id && it.endTimeMs <= c.startTimeMs }.maxOfOrNull { it.endTimeMs } ?: 0L
            val after = track.clips.filter { it.id != c.id && it.startTimeMs >= c.endTimeMs }.minOfOrNull { it.startTimeMs }
            return Edges(
                c.playbackSpeed.toDouble(), c.sourceStartMs, c.sourceEndMs, c.sourceDurationMs, false, c.startTimeMs,
                roomBefore = c.startTimeMs - before, roomAfter = after?.let { it - c.endTimeMs } ?: Long.MAX_VALUE,
            )
        }
        t.findPip(clipId)?.let { c -> return Edges(c.speed.toDouble(), c.sourceStartMs, c.sourceEndMs, c.sourceDurationMs, false, c.startTimeMs) }
        return null
    }

    /**
     * The part of [deltaMs] (timeline ms, positive = right) that [edge] of [clipId] can actually move: limited by the
     * source, the minimum length, time zero and (for audio) the neighbours on the lane. 0 when the clip cannot be trimmed.
     */
    fun clampDelta(t: Timeline, clipId: String, edge: TrimEdge, deltaMs: Long): Long {
        val e = edgesOf(t, clipId) ?: return 0
        val minSource = MIN_CLIP_MS * e.speed
        // Which source end the timeline edge drives, and in which direction (a reversed clip mirrors both).
        val drivesSourceEnd = (edge == TrimEdge.END) != e.reversed
        val sign = if (e.reversed) -1.0 else 1.0
        // Allowed range of sign * delta * speed applied to that source end.
        val (lo, hi) = if (drivesSourceEnd) (e.sourceStart + minSource - e.sourceEnd) to (e.sourceDuration - e.sourceEnd).toDouble()
        else -e.sourceStart.toDouble() to (e.sourceEnd - minSource - e.sourceStart)
        var min = minOf(lo * sign, hi * sign) / e.speed
        var max = maxOf(lo * sign, hi * sign) / e.speed
        if (edge == TrimEdge.START && e.placedAt != null) min = maxOf(min, -e.placedAt.toDouble())
        if (edge == TrimEdge.START) min = maxOf(min, -e.roomBefore.toDouble())
        if (edge == TrimEdge.END) max = minOf(max, e.roomAfter.toDouble())
        if (min > max) return 0
        // Rounded toward zero, so the clamped move never breaks a limit.
        return when {
            deltaMs < min -> kotlin.math.ceil(min).toLong()
            deltaMs > max -> kotlin.math.floor(max).toLong()
            else -> deltaMs
        }.let { if (abs(it) < 1) 0 else it }
    }

    /** Moves [edge] of [clipId] by [deltaMs] (clamped as [clampDelta]); fails when nothing would change. */
    fun apply(t: Timeline, clipId: String, edge: TrimEdge, deltaMs: Long): EditResult {
        if (!t.contains(clipId)) return EditResult.Failure(EditError.ClipNotFound(clipId))
        if (t.videoClips.any { it.id == clipId && it.isFreeze }) return EditResult.Failure(EditError.NotForFreezeFrame)
        val d = clampDelta(t, clipId, edge, deltaMs)
        if (d == 0L) return EditResult.Failure(EditError.InvalidTrimRange)
        fun shift(start: Long, end: Long, speed: Double, reversed: Boolean, duration: Long): Pair<Long, Long> {
            val source = (d * speed).roundToLong()
            val drivesEnd = (edge == TrimEdge.END) != reversed
            val signed = if (reversed) -source else source
            return if (drivesEnd) start to (end + signed).coerceIn(start + 1, duration) else (start + signed).coerceIn(0, end - 1) to end
        }
        t.videoClips.indexOfFirst { it.id == clipId }.takeIf { it >= 0 }?.let { i ->
            val c = t.videoClips[i]
            val (a, b) = shift(c.sourceStartMs, c.sourceEndMs, c.playbackSpeed.toDouble(), c.reversed, c.sourceDurationMs)
            return EditResult.Success(t.copy(videoClips = t.videoClips.toMutableList().also { it[i] = c.copy(sourceStartMs = a, sourceEndMs = b) }).normalized())
        }
        t.audioTracks.forEachIndexed { ti, track ->
            val c = track.clips.firstOrNull { it.id == clipId } ?: return@forEachIndexed
            val (a, b) = shift(c.sourceStartMs, c.sourceEndMs, c.playbackSpeed.toDouble(), false, c.sourceDurationMs)
            val start = if (edge == TrimEdge.START) (c.startTimeMs + d).coerceAtLeast(0) else c.startTimeMs
            val updated = c.copy(sourceStartMs = a, sourceEndMs = b, startTimeMs = start)
            val tracks = t.audioTracks.toMutableList()
            tracks[ti] = track.copy(clips = track.clips.map { if (it.id == clipId) updated else it }.sortedBy { it.startTimeMs })
            return EditResult.Success(t.copy(audioTracks = tracks).normalized())
        }
        val p = t.findPip(clipId)!!
        val (a, b) = shift(p.sourceStartMs, p.sourceEndMs, p.speed.toDouble(), false, p.sourceDurationMs)
        val start = if (edge == TrimEdge.START) (p.startTimeMs + d).coerceAtLeast(0) else p.startTimeMs
        val trimmed = p.copy(sourceStartMs = a, sourceEndMs = b, startTimeMs = start)
        val fitted = trimmed.copy(keyframes = PipKeyframes.fitted(p.keyframes, trimmed.durationMs))
        return EditResult.Success(t.copy(pipClips = t.pipClips.map { if (it.id == clipId) fitted else it }).normalized())
    }
}

/**
 * Step 24: deleting so that nothing is left out of step. Deleting a video clip closes the video track up anyway; a ripple
 * delete also pulls the audio and picture-in-picture clips after it back by the same amount, so music and overlays stay
 * where they were against the picture. Deleting an audio clip with ripple pulls the later clips on its lane back.
 */
object RippleDelete {
    fun remove(t: Timeline, clipId: String): EditResult {
        t.videoClips.firstOrNull { it.id == clipId }?.let { clip ->
            val after = t.copy(videoClips = t.videoClips.filter { it.id != clipId }).normalized()
            val gap = t.videoDurationMs - after.videoDurationMs
            val cutStart = clip.startTimeMs
            val cutEnd = cutStart + gap
            fun moved(start: Long) = when {
                start >= cutEnd -> start - gap
                start >= cutStart -> cutStart
                else -> start
            }
            val tracks = after.audioTracks.map { track -> track.copy(clips = track.clips.map { it.copy(startTimeMs = moved(it.startTimeMs)) }.sortedBy { it.startTimeMs }) }
            if (tracks.any { track -> track.clips.zipWithNext().any { (a, b) -> b.startTimeMs < a.endTimeMs } }) return EditResult.Failure(EditError.AudioOverlap)
            return EditResult.Success(after.copy(audioTracks = tracks, pipClips = after.pipClips.map { it.copy(startTimeMs = moved(it.startTimeMs)) }).normalized())
        }
        t.audioTracks.forEachIndexed { ti, track ->
            val clip = track.clips.firstOrNull { it.id == clipId } ?: return@forEachIndexed
            val rest = track.clips.filter { it.id != clipId }.map { if (it.startTimeMs >= clip.endTimeMs) it.copy(startTimeMs = it.startTimeMs - clip.durationMs) else it }
            val tracks = t.audioTracks.toMutableList().also { it[ti] = track.copy(clips = rest) }.filter { it.clips.isNotEmpty() }
            return EditResult.Success(t.copy(audioTracks = tracks).normalized())
        }
        // Picture-in-picture clips overlap freely; there is nothing to close up.
        return TimelineOperations.remove(t, clipId)
    }

    /** Deletes every clip in [clipIds] as one edit, with or without ripple; fails if any of them is unknown. */
    fun removeAll(t: Timeline, clipIds: Collection<String>, ripple: Boolean): EditResult {
        if (clipIds.isEmpty()) return EditResult.Failure(EditError.NothingSelected)
        var current = t
        // Later clips first, so each ripple only moves clips that are not being deleted.
        clipIds.firstOrNull { startOf(t, it) == null }?.let { return EditResult.Failure(EditError.ClipNotFound(it)) }
        val ordered = clipIds.distinct().sortedByDescending { startOf(t, it) ?: 0L }
        for (id in ordered) {
            current = when (val r = if (ripple) remove(current, id) else TimelineOperations.remove(current, id)) {
                is EditResult.Success -> r.timeline
                is EditResult.Failure -> return r
            }
        }
        return EditResult.Success(current)
    }

    private fun startOf(t: Timeline, id: String): Long? = t.findClip(id)?.startTimeMs ?: t.findPip(id)?.startTimeMs
}

/** Step 24: magnetic edges. A dragged edge or clip within a few pixels of another clip's edge or the playhead sticks to it. */
object Snapping {
    /** Time zero, the playhead and every clip edge on the timeline except those of [excludeId]. */
    fun points(t: Timeline, playheadMs: Long, excludeId: String?): List<Long> {
        val edges = mutableListOf(0L, playheadMs)
        t.videoClips.filter { it.id != excludeId }.forEach { edges += it.startTimeMs; edges += it.endTimeMs }
        t.audioClips.filter { it.id != excludeId }.forEach { edges += it.startTimeMs; edges += it.endTimeMs }
        t.pipClips.filter { it.id != excludeId }.forEach { edges += it.startTimeMs; edges += it.endTimeMs }
        return edges.distinct().sorted()
    }

    /** The nearest point within [thresholdMs] of [ms], or null when none is that close. */
    fun target(ms: Long, points: List<Long>, thresholdMs: Long): Long? =
        points.minByOrNull { abs(it - ms) }?.takeIf { abs(it - ms) <= thresholdMs }

    fun snap(ms: Long, points: List<Long>, thresholdMs: Long): Long = target(ms, points, thresholdMs) ?: ms

    /**
     * A clip [lengthMs] long moved to start at [startMs]: its start snaps if it can, otherwise its end does.
     * Returns the (possibly unchanged) start.
     */
    fun snapMove(startMs: Long, lengthMs: Long, points: List<Long>, thresholdMs: Long): Long {
        target(startMs, points, thresholdMs)?.let { return it }
        target(startMs + lengthMs, points, thresholdMs)?.let { return it - lengthMs }
        return startMs
    }
}
