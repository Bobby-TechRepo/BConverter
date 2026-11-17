package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.model.MediaSource

/** An undoable edit (spec §25). [undo] restores the timeline as it was before [execute]. */
interface EditCommand {
    fun execute(timeline: Timeline): EditResult
    fun undo(current: Timeline): Timeline
    fun redo(timeline: Timeline): EditResult = execute(timeline)
}

/** Remembers the timeline from before a successful [execute]; undo simply restores it (timelines are immutable). */
abstract class SnapshotCommand : EditCommand {
    private var before: Timeline? = null

    protected abstract fun apply(timeline: Timeline): EditResult

    final override fun execute(timeline: Timeline): EditResult {
        val result = apply(timeline)
        if (result is EditResult.Success) before = timeline
        return result
    }

    final override fun undo(current: Timeline): Timeline = checkNotNull(before) { "undo() requires a successful execute() first" }
}

class AddVideoClipCommand(private val clip: VideoClip, private val index: Int) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.insertVideo(timeline, clip, index)
}

class AddAudioClipCommand(private val clip: AudioClip, private val startMs: Long, private val trackId: String) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.insertAudio(timeline, clip, startMs, trackId)
}

class DeleteClipCommand(private val clipId: String) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.remove(timeline, clipId)
}

/** [newClipId] is fixed up front so redo recreates exactly the same clip. */
class SplitClipCommand(private val clipId: String, private val positionMs: Long, private val newClipId: String) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.split(timeline, clipId, positionMs, newClipId)
}

/** Step 24: one edge of a clip dragged on the timeline. */
class TrimEdgeCommand(private val clipId: String, private val edge: TrimEdge, private val deltaMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = EdgeTrim.apply(timeline, clipId, edge, deltaMs)
}

/** Step 24: one or more clips deleted as one edit, optionally closing up what came after them. */
class DeleteClipsCommand(private val clipIds: List<String>, private val ripple: Boolean) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = RippleDelete.removeAll(timeline, clipIds, ripple)
}

class TrimClipCommand(private val clipId: String, private val startMs: Long, private val endMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.trim(timeline, clipId, startMs, endMs)
}

class SetTransitionCommand(private val clipId: String, private val transition: Transition) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setTransition(timeline, clipId, transition)
}

class SetFilterCommand(private val clipId: String, private val filter: ClipFilter) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setFilter(timeline, clipId, filter)
}

class SetFilterForAllCommand(private val filter: ClipFilter) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setFilterForAll(timeline, filter)
}

class SetTransformCommand(private val clipId: String, private val transform: ClipTransform) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setTransform(timeline, clipId, transform)
}

class SetTransformForAllCommand(private val transform: ClipTransform) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setTransformForAll(timeline, transform)
}

class SetKenBurnsCommand(private val clipId: String, private val kenBurns: io.bsoft.bconverter.domain.transform.KenBurns) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setKenBurns(timeline, clipId, kenBurns)
}

class SetKenBurnsForAllCommand(private val kenBurns: io.bsoft.bconverter.domain.transform.KenBurns) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setKenBurnsForAll(timeline, kenBurns)
}

class SetSoundCommand(private val clipId: String, private val sound: ClipSound) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setSound(timeline, clipId, sound)
}

class SetReversedCommand(private val clipId: String, private val reversed: Boolean) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setReversed(timeline, clipId, reversed)
}

class InsertFreezeCommand(private val clipId: String, private val positionMs: Long, private val freezeId: String, private val secondHalfId: String, private val freezeMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.insertFreeze(timeline, clipId, positionMs, freezeId, secondHalfId, freezeMs)
}

class SetFreezeLengthCommand(private val clipId: String, private val freezeMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setFreezeLength(timeline, clipId, freezeMs)
}

class AddPipCommand(private val clip: PipClip) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.addPip(timeline, clip)
}

class MovePipCommand(private val clipId: String, private val startMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.movePip(timeline, clipId, startMs)
}

class TrimPipCommand(private val clipId: String, private val startMs: Long, private val endMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.trimPip(timeline, clipId, startMs, endMs)
}

class SetPipLayoutCommand(private val clipId: String, private val layout: PipLayout) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipLayout(timeline, clipId, layout)
}

class SetPipVolumeCommand(private val clipId: String, private val volume: Float) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipVolume(timeline, clipId, volume)
}

class SetPipMotionCommand(private val clipId: String, private val motion: PipMotion?) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipMotion(timeline, clipId, motion)
}

class SetPipSpeedCommand(private val clipId: String, private val speed: Float) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipSpeed(timeline, clipId, speed)
}

class SetPipShapeCommand(private val clipId: String, private val shape: PipShape) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipShape(timeline, clipId, shape)
}

class SetPipKeyframesCommand(private val clipId: String, private val keyframes: List<PipKeyframe>) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipKeyframes(timeline, clipId, keyframes)
}

class SetPipChromaKeyCommand(private val clipId: String, private val key: ChromaKey?) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipChromaKey(timeline, clipId, key)
}

class SetPipFadesCommand(private val clipId: String, private val fadeInMs: Long, private val fadeOutMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.setPipFades(timeline, clipId, fadeInMs, fadeOutMs)
}

class BringPipToFrontCommand(private val clipId: String) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.bringPipToFront(timeline, clipId)
}

class DetachAudioCommand(private val clipId: String, private val audioClipId: String, private val trackId: String) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.detachAudio(timeline, clipId, audioClipId, trackId)
}

class MoveClipCommand(private val clipId: String, private val toIndex: Int) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.moveVideo(timeline, clipId, toIndex)
}

class MoveAudioClipCommand(private val clipId: String, private val startMs: Long, private val trackIndex: Int? = null) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.moveAudio(timeline, clipId, startMs, trackIndex)
}

class ChangeSpeedCommand(private val clipId: String, private val speed: Float) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.changeSpeed(timeline, clipId, speed)
}

class ChangeVolumeCommand(private val clipId: String, private val volume: Float) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.changeVolume(timeline, clipId, volume)
}

class ReplaceClipCommand(private val clipId: String, private val source: MediaSource, private val sourceDurationMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.replace(timeline, clipId, source, sourceDurationMs)
}

/** Undo/redo stacks with a size limit; failed commands are never recorded and a new command clears redo. */
class CommandHistory(private val limit: Int = DEFAULT_LIMIT) {
    init { require(limit >= 1) { "limit must be >= 1" } }

    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(command: EditCommand, timeline: Timeline): EditResult {
        val result = command.execute(timeline)
        if (result is EditResult.Success) {
            undoStack.addLast(command)
            redoStack.clear()
            while (undoStack.size > limit) undoStack.removeFirst()
        }
        return result
    }

    /** The timeline after undoing the latest command, or null if there is nothing to undo. */
    fun undo(timeline: Timeline): Timeline? {
        val command = undoStack.removeLastOrNull() ?: return null
        val restored = command.undo(timeline)
        redoStack.addLast(command)
        return restored
    }

    fun redo(timeline: Timeline): Timeline? {
        val command = redoStack.removeLastOrNull() ?: return null
        return when (val result = command.redo(timeline)) {
            is EditResult.Success -> { undoStack.addLast(command); result.timeline }
            is EditResult.Failure -> { redoStack.clear(); null }
        }
    }

    fun clear() { undoStack.clear(); redoStack.clear() }

    companion object { const val DEFAULT_LIMIT = 100 }
}
