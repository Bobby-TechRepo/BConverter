package io.bsoft.bconverter.domain.timeline

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

class TrimClipCommand(private val clipId: String, private val startMs: Long, private val endMs: Long) : SnapshotCommand() {
    override fun apply(timeline: Timeline) = TimelineOperations.trim(timeline, clipId, startMs, endMs)
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
