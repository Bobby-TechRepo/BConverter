package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.model.MediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditorState(
    val timeline: Timeline = Timeline(),
    val selectedClipId: String? = null,
    val playheadMs: Long = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/** The editing surface used by the editor UI (spec §24). Business logic lives here, not in Composables or ViewModels. */
interface TimelineEditor {
    val state: StateFlow<EditorState>

    fun splitClip(clipId: String, positionMs: Long): EditResult
    fun trimClip(clipId: String, startMs: Long, endMs: Long): EditResult
    fun removeClip(clipId: String): EditResult
    fun changeSpeed(clipId: String, speed: Float): EditResult
    fun insertVideoClip(clip: VideoClip, index: Int): EditResult
    fun insertAudioClip(clip: AudioClip, startMs: Long): EditResult
    fun moveVideoClip(clipId: String, toIndex: Int): EditResult
    fun moveAudioClip(clipId: String, startMs: Long, trackIndex: Int? = null): EditResult
    fun changeVolume(clipId: String, volume: Float): EditResult
    fun replaceClip(clipId: String, source: MediaSource, sourceDurationMs: Long): EditResult

    /** Returns false (and keeps the selection) if the clip does not exist; null clears the selection. */
    fun select(clipId: String?): Boolean
    fun seekTo(positionMs: Long)
    fun splitSelectedAtPlayhead(): EditResult
    fun undo(): Boolean
    fun redo(): Boolean
}

/** Applies every edit as an undoable command and keeps selection and playhead consistent with the timeline. */
class DefaultTimelineEditor(
    initial: Timeline = Timeline(),
    private val ids: IdGenerator = UuidIdGenerator,
    historyLimit: Int = CommandHistory.DEFAULT_LIMIT,
) : TimelineEditor {
    private val history = CommandHistory(historyLimit)
    private val _state = MutableStateFlow(EditorState(timeline = initial.normalized()))
    override val state: StateFlow<EditorState> = _state.asStateFlow()

    private fun publish(timeline: Timeline) {
        _state.update {
            it.copy(
                timeline = timeline,
                selectedClipId = it.selectedClipId?.takeIf { id -> timeline.contains(id) },
                playheadMs = it.playheadMs.coerceIn(0, timeline.durationMs),
                canUndo = history.canUndo,
                canRedo = history.canRedo,
            )
        }
    }

    private fun run(command: EditCommand): EditResult {
        val result = history.execute(command, _state.value.timeline)
        if (result is EditResult.Success) publish(result.timeline)
        return result
    }

    override fun splitClip(clipId: String, positionMs: Long) = run(SplitClipCommand(clipId, positionMs, ids.next()))
    override fun trimClip(clipId: String, startMs: Long, endMs: Long) = run(TrimClipCommand(clipId, startMs, endMs))
    override fun removeClip(clipId: String) = run(DeleteClipCommand(clipId))
    override fun changeSpeed(clipId: String, speed: Float) = run(ChangeSpeedCommand(clipId, speed))
    override fun insertVideoClip(clip: VideoClip, index: Int) = run(AddVideoClipCommand(clip, index))
    override fun insertAudioClip(clip: AudioClip, startMs: Long) = run(AddAudioClipCommand(clip, startMs, ids.next()))
    override fun moveVideoClip(clipId: String, toIndex: Int) = run(MoveClipCommand(clipId, toIndex))
    override fun moveAudioClip(clipId: String, startMs: Long, trackIndex: Int?) = run(MoveAudioClipCommand(clipId, startMs, trackIndex))
    override fun changeVolume(clipId: String, volume: Float) = run(ChangeVolumeCommand(clipId, volume))
    override fun replaceClip(clipId: String, source: MediaSource, sourceDurationMs: Long) = run(ReplaceClipCommand(clipId, source, sourceDurationMs))

    override fun select(clipId: String?): Boolean {
        if (clipId != null && !_state.value.timeline.contains(clipId)) return false
        _state.update { it.copy(selectedClipId = clipId) }
        return true
    }

    override fun seekTo(positionMs: Long) {
        _state.update { it.copy(playheadMs = positionMs.coerceIn(0, it.timeline.durationMs)) }
    }

    override fun splitSelectedAtPlayhead(): EditResult {
        val s = _state.value
        val selected = s.selectedClipId ?: return EditResult.Failure(EditError.NothingSelected)
        return splitClip(selected, s.playheadMs)
    }

    override fun undo(): Boolean {
        val restored = history.undo(_state.value.timeline) ?: return false
        publish(restored)
        return true
    }

    override fun redo(): Boolean {
        val restored = history.redo(_state.value.timeline) ?: return false
        publish(restored)
        return true
    }
}
