package io.bsoft.bconverter.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayEdit
import io.bsoft.bconverter.domain.overlay.OverlayGestureSession
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.overlay.TextOverlayDefaults
import io.bsoft.bconverter.domain.overlay.TextOverlayEditor
import io.bsoft.bconverter.domain.overlay.TextOverlayStyle
import io.bsoft.bconverter.domain.project.AutosaveResult
import io.bsoft.bconverter.domain.project.ContentSnapshot
import io.bsoft.bconverter.domain.project.EditLog
import io.bsoft.bconverter.domain.project.EditStep
import io.bsoft.bconverter.domain.project.MissingMediaChecker
import io.bsoft.bconverter.domain.project.OpenResult
import io.bsoft.bconverter.domain.project.ProjectAutosaver
import io.bsoft.bconverter.domain.project.ProjectOpener
import io.bsoft.bconverter.domain.project.ProjectRepository
import io.bsoft.bconverter.domain.project.TimeSource
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.domain.subtitle.EditProblem
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleEdit
import io.bsoft.bconverter.domain.subtitle.SubtitleEditor
import io.bsoft.bconverter.domain.subtitle.SubtitleExportPlanner
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import io.bsoft.bconverter.domain.usecase.SubtitleImportResult
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.UriPermissionPersister
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.DefaultTimelineEditor
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.timeline.EditResult
import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.domain.timeline.TimelineEditor
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportSettingsFactory
import io.bsoft.bconverter.domain.usecase.ExportStart
import io.bsoft.bconverter.domain.usecase.OutputNames
import io.bsoft.bconverter.domain.usecase.ProjectExporter
import io.bsoft.bconverter.domain.usecase.SourceLibrary
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ties the pure editing logic to real files and the UI: imports clips (probing and staging them through a
 * [SourceLibrary]), forwards edits to a [TimelineEditor], and runs export as a background job that outlives the screen.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    prober: MediaProber,
    resolver: InputResolver,
    private val capabilities: CapabilitySource,
    private val exporter: ProjectExporter,
    private val jobs: ExportJobLauncher,
    private val permissions: UriPermissionPersister,
    private val ids: IdGenerator,
    projects: ProjectRepository,
    private val opener: ProjectOpener,
    time: TimeSource,
    private val subtitleImporter: io.bsoft.bconverter.domain.usecase.SubtitleImporter,
    private val imageImporter: io.bsoft.bconverter.domain.overlay.OverlayImageImporter,
    /** Survives process death: holds the id of the project being edited once it has been stored. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val library = SourceLibrary(prober, resolver)
    private val editor: TimelineEditor = DefaultTimelineEditor(ids = ids)
    private val autosaver = ProjectAutosaver(projects, time)
    private val _state = MutableStateFlow(EditorUiState(projectId = UUID.randomUUID().toString()))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private var pendingExport: VideoProject? = null
    /** The project being reopened, if any; an export destination delivered meanwhile waits for it. */
    private var restoreJob: Job? = null
    private var exportJobId: String? = null
    private var exportInputs: List<ResolvedInput> = emptyList()
    private var exportStatusJob: Job? = null

    /** What to save, captured when the edit happened; only the newest pending one is kept, and saves run one at a time. */
    private class SaveRequest(
        val id: String, val name: String, val timeline: Timeline, val preset: ExportPreset, val container: ContainerFormat,
        val subtitleTracks: List<SubtitleTrack>,
        val textOverlays: List<io.bsoft.bconverter.domain.overlay.TextOverlay>,
        val canvas: io.bsoft.bconverter.domain.canvas.CanvasSettings,
        val size: io.bsoft.bconverter.domain.model.ExportSize = io.bsoft.bconverter.domain.model.ExportSize.AUTO,
    )
    private val pendingSave = Channel<SaveRequest>(Channel.CONFLATED)
    /** True once the project exists in storage (saved or opened); an untouched new project is never stored. */
    private var persisted = false
    /** Sources that cannot be read right now (missing, revoked, or failed to prepare). */
    private val unusableSources = mutableSetOf<MediaSource>()

    init {
        viewModelScope.launch { for (request in pendingSave) withContext(NonCancellable) { persist(request) } }
        viewModelScope.launch {
            val containers = capabilities.repository().getContainers()
            _state.update {
                it.copy(
                    exportContainers = containers,
                    exportContainer = if (ContainerFormat.MP4 in containers) ContainerFormat.MP4 else containers.firstOrNull() ?: it.exportContainer,
                )
            }
        }
    }

    // ---------------- helpers ----------------
    /** One undo order across the timeline, subtitles and text overlays (see [EditLog]). */
    private val log = EditLog()
    private var committedContent = ContentSnapshot.Empty
    private var committedTimeline: Timeline = editor.state.value.timeline

    private fun content() = _state.value.let { ContentSnapshot(it.subtitleTracks, it.textOverlays, it.canvas) }

    /** Called after every edit is saved: notes what changed since the last call as one undo step per kind. */
    private fun recordChanges() {
        val timeline = editor.state.value.timeline
        if (timeline != committedTimeline) { committedTimeline = timeline; log.recordTimeline() }
        val now = content()
        if (now != committedContent) { log.recordContent(committedContent); committedContent = now }
        _state.update { it.copy(editor = it.editor.copy(canUndo = log.canUndo, canRedo = log.canRedo)) }
    }

    private fun refresh() {
        val current = editor.state.value.let { it.copy(canUndo = log.canUndo, canRedo = log.canRedo) }
        _state.update {
            it.copy(
                editor = current, missingSources = missingFor(current.timeline), previewTimeline = library.staged(current.timeline),
                selectedSourceSize = sourceSizeOfSelected(current),
                multiSelection = it.multiSelection?.filterTo(mutableSetOf()) { id -> current.timeline.contains(id) },
                frameAspect = referenceSize(current.timeline, it.canvas)?.takeIf { r -> r.width > 0 && r.height > 0 }?.let { r -> r.width.toFloat() / r.height } ?: it.frameAspect,
            )
        }
    }

    private fun sourceSizeOfSelected(e: io.bsoft.bconverter.domain.timeline.EditorState): Resolution? {
        val clip = e.selectedClipId?.let { e.timeline.findClip(it) } as? VideoClip ?: return null
        val stream = library.metadata(clip.source)?.videoStreams?.firstOrNull() ?: return null
        return Resolution(stream.displayWidth, stream.displayHeight)
    }

    private fun missingFor(timeline: Timeline) =
        if (unusableSources.isEmpty()) emptyList() else MissingMediaChecker { it !in unusableSources }.check(timeline)

    // ---------------- autosave ----------------
    private fun autosave() {
        recordChanges()
        val s = _state.value
        val timeline = editor.state.value.timeline
        if (timeline.clipCount == 0 && s.subtitleTracks.isEmpty() && s.textOverlays.isEmpty() && !persisted) return
        pendingSave.trySend(SaveRequest(s.projectId, s.projectName, timeline, s.exportPreset, s.exportContainer, s.subtitleTracks, s.textOverlays, s.canvas, s.exportSize))
    }

    private suspend fun persist(r: SaveRequest) {
        val export = ExportSettingsFactory(capabilities.repository()).create(r.preset, r.container, referenceSize(r.timeline, r.canvas), r.size)
            ?: ExportSettings(r.container, VideoSettings(VideoCodec.H264, VideoQuality.Crf(23)), AudioSettings(AudioCodec.AAC, 128))
        when (autosaver.save(VideoProject(r.id, r.name, r.timeline, export, subtitleTracks = r.subtitleTracks, textOverlays = r.textOverlays, canvas = r.canvas, exportChoices = io.bsoft.bconverter.domain.model.ExportChoices(r.preset, r.size)))) {
            is AutosaveResult.Saved, AutosaveResult.Unchanged -> { persisted = true; savedState[KEY_PROJECT_ID] = r.id }
            is AutosaveResult.Failed -> setMessage(EditorMessage.ProjectSaveFailed)
        }
    }
    private fun setMessage(message: EditorMessage?) { _state.update { it.copy(message = message) } }
    private fun report(result: EditResult) {
        refresh()
        when (result) {
            is EditResult.Success -> autosave()
            is EditResult.Failure -> setMessage(EditorMessage.EditFailed(result.error))
        }
    }

    private fun withSelection(operation: (String) -> EditResult) {
        val id = editor.state.value.selectedClipId
        if (id == null) setMessage(EditorMessage.EditFailed(EditError.NothingSelected)) else report(operation(id))
    }

    // ---------------- importing ----------------
    /** Picks are processed one at a time, in the order they were made; a pick during another import waits instead of being dropped. */
    private val importQueue = Mutex()

    private fun import(uri: Uri?, onAdded: (SourceLibrary.AddResult.Added) -> Unit) {
        if (uri == null) return
        viewModelScope.launch {
            importQueue.withLock {
                _state.update { it.copy(busy = true, message = null) }
                try {
                    permissions.persist(uri)
                    when (val result = library.add(uri)) {
                        is SourceLibrary.AddResult.Failed -> setMessage(EditorMessage.ProbeFailed(result.failure))
                        is SourceLibrary.AddResult.Added -> { unusableSources.remove(result.source); onAdded(result) }
                    }
                } finally {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun reject(added: SourceLibrary.AddResult.Added, message: EditorMessage) {
        if (added.newlyStaged) library.discard(added.source)
        setMessage(message)
    }

    /** [uri] is null when the picker was dismissed. The clip is appended to the end of the video track. */
    fun onVideoPicked(uri: Uri?) = import(uri) { added ->
        val duration = added.metadata.durationMs
        when {
            !added.metadata.hasVideo -> reject(added, EditorMessage.NoVideoInFile)
            duration <= 0 -> reject(added, EditorMessage.UnknownDuration)
            else -> report(
                editor.insertVideoClip(VideoClip(ids.next(), added.source, duration, 0, duration), editor.state.value.timeline.videoClips.size),
            )
        }
    }

    /** Several clips from the Photo Picker: appended in the order picked (the import queue keeps the order). */
    fun onVideosPicked(uris: List<Uri>) = uris.forEach(::onVideoPicked)

    /** The audio of any file (including a video's soundtrack) is placed at the playhead. */
    fun onAudioPicked(uri: Uri?) = import(uri) { added ->
        val duration = added.metadata.durationMs
        when {
            !added.metadata.hasAudio -> reject(added, EditorMessage.NoAudioInFile)
            duration <= 0 -> reject(added, EditorMessage.UnknownDuration)
            else -> report(
                editor.insertAudioClip(AudioClip(ids.next(), added.source, duration, 0, duration), editor.state.value.playheadMs),
            )
        }
    }

    /** A video from the picker goes over the main video from the playhead on, as picture in picture; its sound is mixed in. */
    fun onPipPicked(uri: Uri?) = import(uri) { added ->
        val duration = added.metadata.durationMs
        when {
            !added.metadata.hasVideo -> reject(added, EditorMessage.NoVideoInFile)
            duration <= 0 -> reject(added, EditorMessage.UnknownDuration)
            else -> {
                val clip = PipClip(ids.next(), added.source, duration, 0, duration, editor.state.value.playheadMs)
                report(editor.addPipClip(clip))
                if (editor.select(clip.id)) refresh()
            }
        }
    }

    /** Swaps the media of the selected clip; the new file must suit the kind of clip. */
    fun onReplacePicked(uri: Uri?) {
        if (uri == null) return
        val state = editor.state.value
        val item = state.selectedClipId?.let { state.timeline.findClip(it) }
        if (item == null) { setMessage(EditorMessage.EditFailed(EditError.NothingSelected)); return }
        import(uri) { added ->
            val duration = added.metadata.durationMs
            when {
                duration <= 0 -> reject(added, EditorMessage.UnknownDuration)
                item is VideoClip && !added.metadata.hasVideo -> reject(added, EditorMessage.NoVideoInFile)
                item is AudioClip && !added.metadata.hasAudio -> reject(added, EditorMessage.NoAudioInFile)
                else -> report(editor.replaceClip(item.id, added.source, duration))
            }
        }
    }

    // ---------------- editing ----------------
    fun select(clipId: String?) { editor.select(clipId); refresh() }
    fun seekTo(positionMs: Long) { editor.seekTo(positionMs); refresh() }
    fun splitAtPlayhead() = report(editor.splitSelectedAtPlayhead())
    fun deleteSelected() = withSelection { editor.removeClip(it) }
    /** Step 24: deletes the selected clip and pulls what came after it back, so sound and overlays stay in step. */
    fun rippleDeleteSelected() = withSelection { editor.removeClips(listOf(it), ripple = true) }
    /** Step 24: drags one edge of a clip by [deltaMs] of timeline time (one undo step). */
    fun trimEdge(clipId: String, edge: io.bsoft.bconverter.domain.timeline.TrimEdge, deltaMs: Long) = report(editor.trimEdge(clipId, edge, deltaMs))

    // ---- selecting several clips (step 24) ----
    fun startMultiSelect() {
        val seed = editor.state.value.selectedClipId
        _state.update { it.copy(multiSelection = setOfNotNull(seed)) }
    }
    fun toggleInSelection(clipId: String) {
        _state.update { s -> s.copy(multiSelection = s.multiSelection?.let { if (clipId in it) it - clipId else it + clipId }) }
    }
    fun endMultiSelect() { _state.update { it.copy(multiSelection = null) } }
    /** Deletes every picked clip as one edit and leaves the selecting mode. */
    fun deleteMultiSelection(ripple: Boolean) {
        val ids = _state.value.multiSelection?.toList().orEmpty()
        if (ids.isEmpty()) { setMessage(EditorMessage.EditFailed(EditError.NothingSelected)); return }
        val result = editor.removeClips(ids, ripple)
        if (result is EditResult.Success) _state.update { it.copy(multiSelection = null) }
        report(result)
    }
    fun trimSelected(startMs: Long, endMs: Long) = withSelection {
        if (editor.state.value.timeline.findPip(it) != null) editor.trimPipClip(it, startMs, endMs) else editor.trimClip(it, startMs, endMs)
    }
    fun movePip(clipId: String, startMs: Long) = report(editor.movePipClip(clipId, startMs))
    fun setPipLayout(layout: PipLayout) = withSelection { editor.setPipLayout(it, layout) }
    fun setPipVolume(volume: Float) = withSelection { editor.setPipVolume(it, volume) }
    fun setPipFades(fadeInMs: Long, fadeOutMs: Long) = withSelection { editor.setPipFades(it, fadeInMs, fadeOutMs) }
    fun setPipShape(shape: io.bsoft.bconverter.domain.timeline.PipShape) = withSelection { editor.setPipShape(it, shape) }
    fun setPipKeyframes(keyframes: List<io.bsoft.bconverter.domain.timeline.PipKeyframe>) = withSelection { editor.setPipKeyframes(it, keyframes) }
    fun setPipSpeed(speed: Float) = withSelection { editor.setPipSpeed(it, speed) }
    fun setPipMotion(motion: io.bsoft.bconverter.domain.timeline.PipMotion?) = withSelection { editor.setPipMotion(it, motion) }
    fun setPipChromaKey(key: io.bsoft.bconverter.domain.timeline.ChromaKey?) = withSelection { editor.setPipChromaKey(it, key) }
    fun bringPipToFront() = withSelection { editor.bringPipToFront(it) }
    fun setFilter(filter: io.bsoft.bconverter.domain.filter.ClipFilter) = withSelection { editor.setClipFilter(it, filter) }
    fun applyFilterToAll(filter: io.bsoft.bconverter.domain.filter.ClipFilter) = report(editor.setFilterForAll(filter))
    fun setTransform(transform: io.bsoft.bconverter.domain.transform.ClipTransform) = withSelection { editor.setClipTransform(it, transform) }
    fun setKenBurns(kenBurns: io.bsoft.bconverter.domain.transform.KenBurns) = withSelection { editor.setKenBurns(it, kenBurns) }
    fun applyKenBurnsToAll(kenBurns: io.bsoft.bconverter.domain.transform.KenBurns) = report(editor.setKenBurnsForAll(kenBurns))
    fun applyTransformToAll(transform: io.bsoft.bconverter.domain.transform.ClipTransform) = report(editor.setTransformForAll(transform))
    /** Reverses the selected clip, or plays it forwards again; refused when it is too long to reverse at the export's picture size. */
    fun toggleReverse() {
        val id = editor.state.value.selectedClipId
        val clip = id?.let { editor.state.value.timeline.findClip(it) } as? VideoClip
        if (clip == null) { setMessage(EditorMessage.EditFailed(EditError.NothingSelected)); return }
        if (!clip.reversed) {
            val size = referenceSize(editor.state.value.timeline)
            val max = io.bsoft.bconverter.domain.render.ReversePolicy.maxDurationMs(size?.width ?: 1920, size?.height ?: 1080, 30.0)
            if (clip.sourceEndMs - clip.sourceStartMs > max) { setMessage(EditorMessage.ReverseTooLong(max)); return }
        }
        report(editor.setClipReversed(clip.id, !clip.reversed))
    }

    /** Holds the picture at the playhead for a couple of seconds: the selected clip is split there and a freeze frame goes between the halves. */
    fun freezeFrameAtPlayhead() {
        val s = editor.state.value
        val id = s.selectedClipId ?: return setMessage(EditorMessage.EditFailed(EditError.NothingSelected))
        report(editor.insertFreezeFrame(id, s.playheadMs, io.bsoft.bconverter.domain.timeline.FreezeFrame.DEFAULT_MS))
    }

    fun setFreezeLength(ms: Long) = withSelection { editor.setFreezeLength(it, ms) }

    fun setSound(sound: io.bsoft.bconverter.domain.audio.ClipSound) = withSelection { editor.setClipSound(it, sound) }
    fun setSpeed(speed: Float) = withSelection { editor.changeSpeed(it, speed) }
    fun setTransition(type: TransitionType, durationMs: Long) = withSelection { editor.setTransition(it, type, durationMs) }
    fun detachAudioFromSelected() {
        val clip = editor.state.value.let { s -> s.selectedClipId?.let { s.timeline.findClip(it) } } as? VideoClip ?: return
        if (library.metadata(clip.source)?.hasAudio == false) { setMessage(EditorMessage.NoAudioInFile); return }
        withSelection { editor.detachAudio(it) }
    }
    fun setVolume(volume: Float) = withSelection { editor.changeVolume(it, volume) }
    fun moveVideoClip(clipId: String, toIndex: Int) = report(editor.moveVideoClip(clipId, toIndex))
    fun moveAudioClip(clipId: String, startMs: Long, laneIndex: Int) = report(editor.moveAudioClip(clipId, startMs, laneIndex))
    fun moveSelectedVideo(toIndex: Int) = withSelection { editor.moveVideoClip(it, toIndex) }
    fun moveSelectedAudio(startMs: Long) = withSelection { editor.moveAudioClip(it, startMs) }
    fun undo() { endOverlayGesture(); step(log.undo(content()), editor::undo) }
    fun redo() { endOverlayGesture(); step(log.redo(content()), editor::redo) }

    private fun step(step: EditStep?, timelineStep: () -> Boolean) {
        when (step) {
            null -> return
            EditStep.Timeline -> { if (!timelineStep()) return; committedTimeline = editor.state.value.timeline }
            is EditStep.Content -> restoreContent(step.snapshot)
        }
        refresh(); autosave()
    }

    private fun restoreContent(snapshot: ContentSnapshot) {
        committedContent = snapshot
        _state.update { s ->
            s.copy(
                subtitleTracks = snapshot.subtitleTracks, textOverlays = snapshot.textOverlays, canvas = snapshot.canvas,
                selectedCue = s.selectedCue?.takeIf { c -> snapshot.subtitleTracks.any { t -> t.id == c.trackId && t.cues.any { it.id == c.cueId } } },
                selectedOverlayId = s.selectedOverlayId?.takeIf { id -> snapshot.textOverlays.any { it.id == id } },
            )
        }
    }
    /** Changes the output shape, fit or background; one undo step. */
    fun setCanvas(canvas: io.bsoft.bconverter.domain.canvas.CanvasSettings) {
        if (canvas == _state.value.canvas) return
        _state.update { it.copy(canvas = canvas) }
        refresh(); autosave()
    }
    /** Sets the output shape (one undo step), the export size and the quality for a place the video is going to. */
    fun applySharePreset(preset: io.bsoft.bconverter.domain.model.SharePreset) {
        setCanvas(_state.value.canvas.copy(aspect = preset.aspect))
        _state.update { it.copy(exportSize = preset.size, exportPreset = preset.quality) }
        autosave()
    }
    fun renameProject(name: String) { _state.update { it.copy(projectName = name) }; autosave() }

    /**
     * Gives a brand-new project the screen's localised default name. A stored or reopened project, or one the user has
     * already renamed, is left alone; nothing is saved by this alone.
     */
    fun applyDefaultName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || persisted || savedState.contains(KEY_PROJECT_ID)) return
        _state.update { if (it.projectName == EditorUiState.DEFAULT_NAME) it.copy(projectName = trimmed) else it }
    }

    // ---------------- subtitles ----------------
    /** [uri] is null when the picker was dismissed. The file is read into a new track; the original file is never changed. */
    fun onSubtitlePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
          importQueue.withLock {
            _state.update { it.copy(busy = true, message = null) }
            permissions.persist(uri)
            when (val result = subtitleImporter.load(uri)) {
                is SubtitleImportResult.Failed -> setMessage(EditorMessage.SubtitleImportFailed(result.reason))
                is SubtitleImportResult.Imported -> {
                    _state.update { it.copy(subtitleTracks = it.subtitleTracks + result.track) }
                    autosave()
                    if (result.warnings.isNotEmpty()) setMessage(EditorMessage.SubtitleImportedWithWarnings(result.warnings.size))
                }
            }
            _state.update { it.copy(busy = false) }
          }
        }
    }

    /** [nameFor] turns the track's number into its name; the screen passes a localised pattern. */
    fun addEmptySubtitleTrack(nameFor: (Int) -> String = { "Subtitles $it" }) {
        val number = _state.value.subtitleTracks.size + 1
        _state.update { it.copy(subtitleTracks = it.subtitleTracks + SubtitleTrack(ids.next(), nameFor(number))) }
        autosave()
    }

    fun removeSubtitleTrack(trackId: String) {
        _state.update { s -> s.copy(subtitleTracks = s.subtitleTracks.filterNot { it.id == trackId }, selectedCue = s.selectedCue?.takeIf { it.trackId != trackId }) }
        autosave()
    }

    fun renameSubtitleTrack(trackId: String, name: String) = changeTrack(trackId) { it.copy(name = name.trim().ifEmpty { it.name }) }

    /** Shifts the whole track in time without touching its cues. */
    fun setSubtitleExportMode(trackId: String, mode: io.bsoft.bconverter.domain.subtitle.SubtitleExportMode) = changeTrack(trackId) { it.copy(exportMode = mode) }
    fun setSubtitleLanguage(trackId: String, language: String) = changeTrack(trackId) { it.copy(language = language.trim().ifEmpty { null }) }
    fun setSubtitleForced(trackId: String, forced: Boolean) = changeTrack(trackId) { it.copy(isForced = forced) }

    /** Only one track can be the default one. */
    fun setSubtitleDefault(trackId: String, isDefault: Boolean) {
        _state.update { s -> s.copy(subtitleTracks = s.subtitleTracks.map { it.copy(isDefault = isDefault && it.id == trackId) }) }
        autosave()
    }

    fun setSubtitleStyle(trackId: String, style: io.bsoft.bconverter.domain.subtitle.SubtitleStyle) = editTrack(trackId) { SubtitleEditor.updateBaseStyle(it, style) }

    fun setSubtitleOffset(trackId: String, offsetMs: Long) = editTrack(trackId) { SubtitleEditor.setOffset(it, offsetMs) }

    fun selectCue(trackId: String?, cueId: String?) {
        _state.update { it.copy(selectedCue = if (trackId != null && cueId != null) CueRef(trackId, cueId) else null) }
    }

    /** A two-second cue at the playhead (as shown, so the track offset is taken off to get its stored time). */
    fun addCueAtPlayhead(trackId: String, playheadMs: Long, text: String = "") {
        val track = _state.value.subtitleTracks.firstOrNull { it.id == trackId } ?: return
        val start = (playheadMs - track.offsetMs).coerceAtLeast(0)
        val cue = SubtitleCue(ids.next(), start, start + DEFAULT_CUE_MS, text)
        editTrack(trackId) { SubtitleEditor.addCue(it, cue) }
        selectCue(trackId, cue.id)
    }

    fun editCueText(trackId: String, cueId: String, text: String) = editTrack(trackId) { SubtitleEditor.editText(it, cueId, text) }
    fun retimeCue(trackId: String, cueId: String, startMs: Long, endMs: Long) = editTrack(trackId) { SubtitleEditor.retime(it, cueId, startMs, endMs) }
    fun moveCue(trackId: String, cueId: String, deltaMs: Long) = editTrack(trackId) { SubtitleEditor.moveCue(it, cueId, deltaMs) }
    fun deleteCue(trackId: String, cueId: String) {
        editTrack(trackId) { SubtitleEditor.deleteCue(it, cueId) }
        _state.update { s -> s.copy(selectedCue = s.selectedCue?.takeIf { it.cueId != cueId }) }
    }

    fun duplicateCue(trackId: String, cueId: String) {
        val copyId = ids.next()
        if (editTrack(trackId) { SubtitleEditor.duplicateCue(it, cueId, copyId) }) selectCue(trackId, copyId)
    }

    fun splitCueAtPlayhead(trackId: String, cueId: String, playheadMs: Long) {
        val offset = _state.value.subtitleTracks.firstOrNull { it.id == trackId }?.offsetMs ?: return
        val newId = ids.next()
        if (editTrack(trackId) { SubtitleEditor.splitCue(it, cueId, playheadMs - offset, newId) }) selectCue(trackId, newId)
    }

    /** Joins the cue with the one that follows it in time. */
    fun mergeCueWithNext(trackId: String, cueId: String) {
        val track = _state.value.subtitleTracks.firstOrNull { it.id == trackId } ?: return
        val index = track.cues.indexOfFirst { it.id == cueId }
        val next = track.cues.getOrNull(index + 1) ?: run { setMessage(EditorMessage.SubtitleEditRejected(EditProblem.NoSuchCue)); return }
        if (editTrack(trackId) { SubtitleEditor.mergeCues(it, cueId, next.id) }) selectCue(trackId, cueId)
    }

    private fun changeTrack(trackId: String, change: (SubtitleTrack) -> SubtitleTrack) {
        _state.update { s -> s.copy(subtitleTracks = s.subtitleTracks.map { if (it.id == trackId) change(it) else it }) }
        autosave()
    }

    /** Applies [operation] to the track; true when it was accepted. A refused edit leaves everything unchanged and says why. */
    private fun editTrack(trackId: String, operation: (SubtitleTrack) -> SubtitleEdit): Boolean {
        val track = _state.value.subtitleTracks.firstOrNull { it.id == trackId } ?: return false
        return when (val result = operation(track)) {
            is SubtitleEdit.Ok -> { changeTrack(trackId) { result.track }; true }
            is SubtitleEdit.Rejected -> { setMessage(EditorMessage.SubtitleEditRejected(result.reason)); false }
        }
    }

    // ---------------- text overlays ----------------
    fun selectOverlay(id: String?) { endOverlayGesture(); _state.update { it.copy(selectedOverlayId = id?.takeIf { x -> it.textOverlays.any { o -> o.id == x } }) } }

    /** A new caption at the playhead; it is selected so it can be dragged and edited at once. */
    fun addTextOverlay(playheadMs: Long, text: String = "Text") {
        val overlay = TextOverlayDefaults.newAt(ids.next(), playheadMs, text)
        if (editOverlays { TextOverlayEditor.add(it, overlay) }) selectOverlay(overlay.id)
    }

    /** A picked picture is copied into the app and placed at the playhead, on top, and selected; nothing is picked when [uri] is null. */
    fun onOverlayImagePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val frameAspect = referenceSize(_state.value.editor.timeline)?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
            when (val r = imageImporter.import(uri.toString(), frameAspect)) {
                is io.bsoft.bconverter.domain.overlay.ImageImport.Imported -> {
                    val overlay = TextOverlayDefaults.newImageAt(ids.next(), editor.state.value.playheadMs, r.image)
                    if (editOverlays { TextOverlayEditor.add(it, overlay) }) selectOverlay(overlay.id)
                }
                io.bsoft.bconverter.domain.overlay.ImageImport.Unreadable -> setMessage(EditorMessage.ImageUnreadable)
                io.bsoft.bconverter.domain.overlay.ImageImport.TooLarge -> setMessage(EditorMessage.ImageTooLarge)
            }
        }
    }

    fun deleteOverlay(id: String) {
        editOverlays { TextOverlayEditor.remove(it, id) }
        _state.update { s -> s.copy(selectedOverlayId = s.selectedOverlayId?.takeIf { it != id }) }
    }

    fun duplicateOverlay(id: String) {
        val copyId = ids.next()
        if (editOverlays { TextOverlayEditor.duplicate(it, id, copyId) }) selectOverlay(copyId)
    }

    fun setOverlayText(id: String, text: String) { editOverlays { TextOverlayEditor.setText(it, id, text) } }
    fun renameOverlay(id: String, name: String?) { editOverlays { TextOverlayEditor.rename(it, id, name) } }
    fun retimeOverlay(id: String, startMs: Long, endMs: Long) { editOverlays { TextOverlayEditor.retime(it, id, startMs, endMs) } }
    fun shiftOverlay(id: String, deltaMs: Long) { editOverlays { TextOverlayEditor.shift(it, id, deltaMs) } }
    fun setOverlayPosition(id: String, x: Float, y: Float) { editOverlays { TextOverlayEditor.moveTo(it, id, x, y) } }
    fun setOverlayRotation(id: String, degrees: Float) { editOverlays { TextOverlayEditor.setRotation(it, id, degrees) } }
    fun setOverlayScale(id: String, scale: Float) { editOverlays { TextOverlayEditor.setScale(it, id, scale) } }
    fun setOverlayWidth(id: String, widthFraction: Float?) { editOverlays { TextOverlayEditor.setWidth(it, id, widthFraction) } }
    fun updateOverlayStyle(id: String, change: (TextOverlayStyle) -> TextOverlayStyle) { editOverlays { TextOverlayEditor.updateStyle(it, id, change) } }
    fun setOverlayKeyframes(id: String, keyframes: List<io.bsoft.bconverter.domain.overlay.OverlayKeyframe>) { editOverlays { TextOverlayEditor.setKeyframes(it, id, keyframes) } }
    fun setOverlayAnimations(id: String, animIn: OverlayAnimation?, animOut: OverlayAnimation?) { editOverlays { TextOverlayEditor.setAnimations(it, id, animIn, animOut) } }

    enum class OverlayLayerMove { FRONT, FORWARD, BACKWARD, BACK }

    fun moveOverlayLayer(id: String, move: OverlayLayerMove) {
        editOverlays {
            when (move) {
                OverlayLayerMove.FRONT -> TextOverlayEditor.bringToFront(it, id)
                OverlayLayerMove.FORWARD -> TextOverlayEditor.bringForward(it, id)
                OverlayLayerMove.BACKWARD -> TextOverlayEditor.sendBackward(it, id)
                OverlayLayerMove.BACK -> TextOverlayEditor.sendToBack(it, id)
            }
        }
    }

    private var gesture: OverlayGestureSession? = null
    private var gestureId: String? = null
    private var gestureChanged = false

    /** Drag, pinch and rotate on the selected overlay: [dx]/[dy] are fractions of the preview, [zoom] a factor, [rotationDeg] the turn. */
    fun transformSelectedOverlay(dx: Float, dy: Float, zoom: Float, rotationDeg: Float) {
        val id = _state.value.selectedOverlayId ?: return
        val current = _state.value.textOverlays.firstOrNull { it.id == id } ?: return
        if (gestureId != id || gesture == null) { gesture = OverlayGestureSession(current); gestureId = id }
        val r = gesture!!.update(dx, dy, zoom, rotationDeg)
        _state.update { s -> s.copy(textOverlays = s.textOverlays.map { if (it.id == id) r.overlay else it }, overlayGuide = OverlayGuide(r.snappedX, r.snappedY)) }
    }

    /** Drag, pinch and twist on a keyframed overlay: at the playhead it moves/zooms/turns the keyframe there, or adds one with the look the overlay has. */
    fun transformOverlayKeyframe(id: String, offsetMs: Long, dx: Float, dy: Float, zoom: Float, rotationDeg: Float = 0f) {
        val o = _state.value.textOverlays.firstOrNull { it.id == id } ?: return
        if (o.keyframes.isEmpty()) return
        val l = io.bsoft.bconverter.domain.overlay.OverlayKeyframes.interpolate(o.keyframes, offsetMs)
        val moved = io.bsoft.bconverter.domain.overlay.OverlayLook(
            if (dx.isFinite()) l.x + dx else l.x, if (dy.isFinite()) l.y + dy else l.y, if (zoom.isFinite() && zoom > 0f) l.zoom * zoom else l.zoom, l.opacity,
            if (rotationDeg.isFinite()) l.rotationDeg + rotationDeg else l.rotationDeg,
        )
        val keys = io.bsoft.bconverter.domain.overlay.OverlayKeyframes.withLookAt(o, offsetMs, moved)
        if (keys == o.keyframes) return
        gesture = null; gestureId = id   // the end of the gesture saves
        _state.update { s -> s.copy(textOverlays = s.textOverlays.map { if (it.id == id) it.copy(keyframes = keys) else it }) }
        gestureChanged = true
    }

    /** The finger lifted: the guide goes away and the result is saved. */
    fun endOverlayGesture() {
        val had = gesture != null || gestureChanged
        gesture = null; gestureId = null; gestureChanged = false
        _state.update { it.copy(overlayGuide = OverlayGuide()) }
        if (had) autosave()
    }

    /** Applies [operation] to the overlay list; true when accepted. A refused edit changes nothing and says why. */
    private fun editOverlays(operation: (List<TextOverlay>) -> OverlayEdit): Boolean =
        when (val r = operation(_state.value.textOverlays)) {
            is OverlayEdit.Ok -> { _state.update { it.copy(textOverlays = r.overlays) }; autosave(); true }
            is OverlayEdit.Rejected -> { setMessage(EditorMessage.OverlayEditRejected(r.reason)); false }
        }

    // ---------------- reopening ----------------
    /** Restores a saved project: timeline, name and export choice; its sources are prepared again and missing ones reported. */
    fun openProject(id: String) {
        if (_state.value.busy) return
        restoreJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            when (val opened = opener.open(id)) {
                OpenResult.NotFound -> setMessage(EditorMessage.ProjectNotFound)
                OpenResult.Corrupt -> setMessage(EditorMessage.ProjectCorrupt)
                is OpenResult.Opened -> restore(opened)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun restore(opened: OpenResult.Opened) {
        val stored = opened.stored
        val timeline = stored.project.timeline
        unusableSources.clear()
        unusableSources += opened.missing.map { it.source }
        (timeline.videoClips.map { it.source } + timeline.audioClips.map { it.source } + timeline.pipClips.map { it.source }).distinct()
            .filterIsInstance<MediaSource.UriSource>()
            .filter { it !in unusableSources }
            .forEach { if (library.add(it.uri) is SourceLibrary.AddResult.Failed) unusableSources += it }
        editor.load(timeline)
        log.clear(); committedTimeline = editor.state.value.timeline
        committedContent = ContentSnapshot(stored.project.subtitleTracks, stored.project.textOverlays, stored.project.canvas)
        autosaver.resume(stored)
        persisted = true
        savedState[KEY_PROJECT_ID] = stored.project.id
        _state.update {
            val container = stored.project.export.container
            it.copy(
                projectId = stored.project.id,
                projectName = stored.project.name,
                subtitleTracks = stored.project.subtitleTracks,
                selectedCue = null,
                textOverlays = stored.project.textOverlays,
                canvas = stored.project.canvas,
                exportPreset = stored.project.exportChoices.quality,
                exportSize = stored.project.exportChoices.size,
                selectedOverlayId = null,
                exportContainer = if (it.exportContainers.isEmpty() || container in it.exportContainers) container else it.exportContainer,
            )
        }
        refresh()
    }

    /** Clears the message and a finished export's "completed" banner. */
    fun dismissMessage() {
        _state.update {
            val done = it.export == ExportUiState.Completed
            it.copy(message = null, export = if (done) ExportUiState.Idle else it.export, savedExport = if (done) null else it.savedExport)
        }
    }

    // ---------------- export ----------------
    fun onExportSizeSelected(size: io.bsoft.bconverter.domain.model.ExportSize) { _state.update { it.copy(exportSize = size) }; autosave() }
    fun onExportPresetSelected(preset: ExportPreset) { _state.update { it.copy(exportPreset = preset) }; autosave() }

    fun onExportContainerSelected(container: ContainerFormat) {
        if (container in _state.value.exportContainers) { _state.update { it.copy(exportContainer = container) }; autosave() }
    }

    /** Checks the project can be exported, then asks the UI for a destination. */
    fun onExportRequested() {
        if (_state.value.export is ExportUiState.Running) return
        viewModelScope.launch {
            val project = prepareExport() ?: return@launch
            pendingExport = project
            _events.trySend(EditorEvent.RequestExportDestination("${project.name.trim()}.${project.export.container.extension}", project.export.container.mimeType))
        }
    }

    /** The project as it would be exported now, or null after saying why it cannot be (name, encoders, plan, subtitles). */
    private suspend fun prepareExport(): VideoProject? {
        val s = _state.value
        if (!OutputNames.isValid(s.projectName)) { setMessage(EditorMessage.InvalidProjectName); return null }
        val settings = ExportSettingsFactory(capabilities.repository()).create(s.exportPreset, s.exportContainer, referenceSize(editor.state.value.timeline, s.canvas), s.exportSize)
        if (settings == null) { setMessage(EditorMessage.NoExportEncoders); return null }
        val project = VideoProject(EXPORT_PROJECT_ID, s.projectName, editor.state.value.timeline, settings, subtitleTracks = s.subtitleTracks, textOverlays = s.textOverlays, canvas = s.canvas)
        (RenderPlanner.plan(library.forRender(project)) as? RenderPlanResult.Failure)?.let {
            setMessage(EditorMessage.ExportRejected(io.bsoft.bconverter.domain.usecase.ExportRejection.NotRenderable(it.error)))
            return null
        }
        SubtitleExportPlanner.plan(project.subtitleTracks, project.export.container).blocking.takeIf { it.isNotEmpty() }?.let {
            setMessage(EditorMessage.ExportRejected(io.bsoft.bconverter.domain.usecase.ExportRejection.SubtitlesNotSupported(it)))
            return null
        }
        return project
    }

    /**
     * [uri] is null when the save dialog was dismissed. If the process was killed while the dialog was open, the pending
     * export is gone: the project is reopened from storage (see [restoreAfterProcessDeath]) and the export rebuilt from it.
     */
    fun onExportDestinationChosen(uri: Uri?) {
        val project = pendingExport
        pendingExport = null
        if (uri == null) return
        if (project != null) { startExport(project, uri); return }
        if (_state.value.export is ExportUiState.Running) return
        viewModelScope.launch {
            restoreJob?.join()
            prepareExport()?.let { startExport(it, uri) }
        }
    }

    private fun startExport(project: VideoProject, uri: Uri) {
        val inputs = library.inputs()
        when (val start = exporter.start(library.forRender(project), uri, inputs)) {
            is ExportStart.Rejected -> setMessage(EditorMessage.ExportRejected(start.reason))
            is ExportStart.Started -> track(start.jobId, start.status, inputs, SavedOutput(uri, project.export.container.mimeType))
        }
    }

    fun onCancelExport() { exportJobId?.let { jobs.cancel(it) } }

    private fun track(jobId: String, status: StateFlow<JobStatus>, inputs: List<ResolvedInput>, saved: SavedOutput) {
        exportJobId = jobId
        exportInputs = inputs
        _state.update { it.copy(export = ExportUiState.Running(0f), savedExport = null) }
        exportStatusJob = viewModelScope.launch {
            val terminal = status
                .onEach { s -> if (s is JobStatus.Running) _state.update { it.copy(export = ExportUiState.Running(s.progress.coerceIn(0f, 1f))) } }
                .first { it.isTerminal }
            jobs.finish(jobId)
            exportJobId = null
            exportInputs = emptyList()
            when (terminal) {
                JobStatus.Completed -> _state.update { it.copy(export = ExportUiState.Completed, savedExport = saved) }
                is JobStatus.Failed -> _state.update {
                    it.copy(
                        export = ExportUiState.Idle,
                        message = when (terminal.reason) {
                            JobFailure.SaveFailed -> EditorMessage.ExportSaveFailed
                            JobFailure.OverlaysFailed -> EditorMessage.ExportOverlaysFailed
                            else -> EditorMessage.ExportFailed
                        },
                    )
                }
                else -> _state.update { it.copy(export = ExportUiState.Idle) } // cancelled
            }
        }
    }

    /** The first clip's display size after its crop and rotation, which an export without a fixed size uses as its canvas. */
    private fun referenceSize(timeline: Timeline, canvas: io.bsoft.bconverter.domain.canvas.CanvasSettings = _state.value.canvas): Resolution? {
        val first = timeline.videoClips.firstOrNull() ?: return null
        val stream = library.metadata(first.source)?.videoStreams?.firstOrNull() ?: return null
        val (w, h) = first.transform.outputSize(stream.displayWidth, stream.displayHeight)
        val (shapedW, shapedH) = canvas.sizeFor(w, h)
        return Resolution(shapedW, shapedH)
    }

    override fun onCleared() {
        // A running export keeps its inputs and releases them itself; everything else is freed now.
        val running = exportJobId
        if (running != null) {
            jobs.detach(running)
            library.releaseAllExcept(exportInputs)
        } else {
            library.releaseAll()
        }
    }

    /** After process death the screen comes back with a fresh ViewModel: reopen the project that was being edited, if it was stored. */
    private fun restoreAfterProcessDeath() {
        savedState.get<String>(KEY_PROJECT_ID)?.let { openProject(it) }
    }

    // Last in the class on purpose: initialisers run in order, and reopening touches the properties declared above.
    init { restoreAfterProcessDeath() }

    private companion object {
        const val EXPORT_PROJECT_ID = "project"
        const val DEFAULT_CUE_MS = 2_000L
        const val KEY_PROJECT_ID = "editor.projectId"
    }
}
