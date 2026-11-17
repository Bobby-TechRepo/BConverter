package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
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
    /** Step 24: drags [edge] of a clip by [deltaMs] of timeline time. */
    fun trimEdge(clipId: String, edge: TrimEdge, deltaMs: Long): EditResult
    /** Step 24: deletes [clipIds] in one edit; with [ripple], later audio and overlay clips close up too. */
    fun removeClips(clipIds: List<String>, ripple: Boolean): EditResult
    fun changeSpeed(clipId: String, speed: Float): EditResult
    fun insertVideoClip(clip: VideoClip, index: Int): EditResult
    fun insertAudioClip(clip: AudioClip, startMs: Long): EditResult
    fun moveVideoClip(clipId: String, toIndex: Int): EditResult
    /** Sets the transition on the cut before [clipId]; [durationMs] is ignored for [TransitionType.NONE]. */
    fun setTransition(clipId: String, type: TransitionType, durationMs: Long): EditResult
    fun moveAudioClip(clipId: String, startMs: Long, trackIndex: Int? = null): EditResult
    /** Mutes a video clip and places its sound on the audio lanes as a separate clip. */
    fun detachAudio(clipId: String): EditResult
    fun changeVolume(clipId: String, volume: Float): EditResult
    fun setClipSound(clipId: String, sound: ClipSound): EditResult
    fun setClipReversed(clipId: String, reversed: Boolean): EditResult
    fun addPipClip(clip: PipClip): EditResult
    fun movePipClip(clipId: String, startMs: Long): EditResult
    fun trimPipClip(clipId: String, startMs: Long, endMs: Long): EditResult
    fun setPipLayout(clipId: String, layout: PipLayout): EditResult
    fun setPipVolume(clipId: String, volume: Float): EditResult
    fun setPipFades(clipId: String, fadeInMs: Long, fadeOutMs: Long): EditResult
    fun setPipChromaKey(clipId: String, key: ChromaKey?): EditResult
    fun setPipSpeed(clipId: String, speed: Float): EditResult
    fun setPipKeyframes(clipId: String, keyframes: List<PipKeyframe>): EditResult
    fun setPipShape(clipId: String, shape: PipShape): EditResult
    fun setPipMotion(clipId: String, motion: PipMotion?): EditResult
    fun bringPipToFront(clipId: String): EditResult
    fun insertFreezeFrame(clipId: String, positionMs: Long, freezeMs: Long): EditResult
    fun setFreezeLength(clipId: String, freezeMs: Long): EditResult
    fun replaceClip(clipId: String, source: MediaSource, sourceDurationMs: Long): EditResult
    fun setClipFilter(clipId: String, filter: ClipFilter): EditResult
    fun setFilterForAll(filter: ClipFilter): EditResult
    fun setClipTransform(clipId: String, transform: ClipTransform): EditResult
    fun setTransformForAll(transform: ClipTransform): EditResult
    fun setKenBurns(clipId: String, kenBurns: io.bsoft.bconverter.domain.transform.KenBurns): EditResult
    fun setKenBurnsForAll(kenBurns: io.bsoft.bconverter.domain.transform.KenBurns): EditResult

    /** Returns false (and keeps the selection) if the clip does not exist; null clears the selection. */
    fun select(clipId: String?): Boolean
    fun seekTo(positionMs: Long)
    fun splitSelectedAtPlayhead(): EditResult
    fun undo(): Boolean
    fun redo(): Boolean

    /** Replaces everything with [timeline] (e.g. a reopened project): selection, playhead and undo history start afresh. */
    fun load(timeline: Timeline)
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
                playheadMs = SeekRange.clamp(timeline, it.playheadMs),
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
    override fun trimEdge(clipId: String, edge: TrimEdge, deltaMs: Long) = run(TrimEdgeCommand(clipId, edge, deltaMs))
    override fun removeClips(clipIds: List<String>, ripple: Boolean) = run(DeleteClipsCommand(clipIds, ripple))
    override fun changeSpeed(clipId: String, speed: Float) = run(ChangeSpeedCommand(clipId, speed))
    override fun insertVideoClip(clip: VideoClip, index: Int) = run(AddVideoClipCommand(clip, index))

    override fun setTransition(clipId: String, type: TransitionType, durationMs: Long): EditResult {
        val transition = when {
            type == TransitionType.NONE -> Transition.None
            durationMs in Transition.MIN_MS..Transition.MAX_MS -> Transition(type, durationMs)
            else -> return EditResult.Failure(EditError.InvalidTransition)
        }
        return run(SetTransitionCommand(clipId, transition))
    }
    override fun detachAudio(clipId: String) = run(DetachAudioCommand(clipId, ids.next(), ids.next()))
    override fun insertAudioClip(clip: AudioClip, startMs: Long) = run(AddAudioClipCommand(clip, startMs, ids.next()))
    override fun moveVideoClip(clipId: String, toIndex: Int) = run(MoveClipCommand(clipId, toIndex))
    override fun moveAudioClip(clipId: String, startMs: Long, trackIndex: Int?) = run(MoveAudioClipCommand(clipId, startMs, trackIndex))
    override fun setClipFilter(clipId: String, filter: ClipFilter) = run(SetFilterCommand(clipId, filter))
    override fun setFilterForAll(filter: ClipFilter) = run(SetFilterForAllCommand(filter))
    override fun setClipTransform(clipId: String, transform: ClipTransform) = run(SetTransformCommand(clipId, transform))
    override fun setTransformForAll(transform: ClipTransform) = run(SetTransformForAllCommand(transform))
    override fun setKenBurns(clipId: String, kenBurns: io.bsoft.bconverter.domain.transform.KenBurns) = run(SetKenBurnsCommand(clipId, kenBurns))
    override fun setKenBurnsForAll(kenBurns: io.bsoft.bconverter.domain.transform.KenBurns) = run(SetKenBurnsForAllCommand(kenBurns))
    override fun changeVolume(clipId: String, volume: Float) = run(ChangeVolumeCommand(clipId, volume))
    override fun setClipSound(clipId: String, sound: ClipSound) = run(SetSoundCommand(clipId, sound))
    override fun setClipReversed(clipId: String, reversed: Boolean) = run(SetReversedCommand(clipId, reversed))
    override fun addPipClip(clip: PipClip) = run(AddPipCommand(clip))
    override fun movePipClip(clipId: String, startMs: Long) = run(MovePipCommand(clipId, startMs))
    override fun trimPipClip(clipId: String, startMs: Long, endMs: Long) = run(TrimPipCommand(clipId, startMs, endMs))
    override fun setPipLayout(clipId: String, layout: PipLayout) = run(SetPipLayoutCommand(clipId, layout))
    override fun setPipVolume(clipId: String, volume: Float) = run(SetPipVolumeCommand(clipId, volume))
    override fun setPipFades(clipId: String, fadeInMs: Long, fadeOutMs: Long) = run(SetPipFadesCommand(clipId, fadeInMs, fadeOutMs))
    override fun setPipChromaKey(clipId: String, key: ChromaKey?) = run(SetPipChromaKeyCommand(clipId, key))
    override fun setPipShape(clipId: String, shape: PipShape) = run(SetPipShapeCommand(clipId, shape))
    override fun setPipKeyframes(clipId: String, keyframes: List<PipKeyframe>) = run(SetPipKeyframesCommand(clipId, keyframes))
    override fun setPipSpeed(clipId: String, speed: Float) = run(SetPipSpeedCommand(clipId, speed))
    override fun setPipMotion(clipId: String, motion: PipMotion?) = run(SetPipMotionCommand(clipId, motion))
    override fun bringPipToFront(clipId: String) = run(BringPipToFrontCommand(clipId))
    override fun insertFreezeFrame(clipId: String, positionMs: Long, freezeMs: Long) = run(InsertFreezeCommand(clipId, positionMs, ids.next(), ids.next(), freezeMs))
    override fun setFreezeLength(clipId: String, freezeMs: Long) = run(SetFreezeLengthCommand(clipId, freezeMs))
    override fun replaceClip(clipId: String, source: MediaSource, sourceDurationMs: Long) = run(ReplaceClipCommand(clipId, source, sourceDurationMs))

    override fun select(clipId: String?): Boolean {
        if (clipId != null && !_state.value.timeline.contains(clipId)) return false
        _state.update { it.copy(selectedClipId = clipId) }
        return true
    }

    override fun seekTo(positionMs: Long) {
        _state.update { it.copy(playheadMs = SeekRange.clamp(it.timeline, positionMs)) }
    }

    override fun splitSelectedAtPlayhead(): EditResult {
        val s = _state.value
        val selected = s.selectedClipId ?: return EditResult.Failure(EditError.NothingSelected)
        return splitClip(selected, s.playheadMs)
    }

    override fun load(timeline: Timeline) {
        history.clear()
        _state.value = EditorState(timeline = timeline.normalized())
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
