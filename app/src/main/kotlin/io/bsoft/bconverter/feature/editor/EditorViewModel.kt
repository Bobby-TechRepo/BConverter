package io.bsoft.bconverter.feature.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
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
import kotlinx.coroutines.Job
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
) : ViewModel() {
    private val library = SourceLibrary(prober, resolver)
    private val editor: TimelineEditor = DefaultTimelineEditor(ids = ids)
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private var pendingExport: VideoProject? = null
    private var exportJobId: String? = null
    private var exportInputs: List<ResolvedInput> = emptyList()
    private var exportStatusJob: Job? = null

    init {
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
    private fun refresh() { _state.update { it.copy(editor = editor.state.value) } }
    private fun setMessage(message: EditorMessage?) { _state.update { it.copy(message = message) } }
    private fun report(result: EditResult) {
        refresh()
        if (result is EditResult.Failure) setMessage(EditorMessage.EditFailed(result.error))
    }

    private fun withSelection(operation: (String) -> EditResult) {
        val id = editor.state.value.selectedClipId
        if (id == null) setMessage(EditorMessage.EditFailed(EditError.NothingSelected)) else report(operation(id))
    }

    // ---------------- importing ----------------
    private fun import(uri: Uri?, onAdded: (SourceLibrary.AddResult.Added) -> Unit) {
        if (uri == null || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            permissions.persist(uri)
            when (val result = library.add(uri)) {
                is SourceLibrary.AddResult.Failed -> setMessage(EditorMessage.ProbeFailed(result.failure))
                is SourceLibrary.AddResult.Added -> onAdded(result)
            }
            _state.update { it.copy(busy = false) }
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
    fun trimSelected(startMs: Long, endMs: Long) = withSelection { editor.trimClip(it, startMs, endMs) }
    fun setSpeed(speed: Float) = withSelection { editor.changeSpeed(it, speed) }
    fun setVolume(volume: Float) = withSelection { editor.changeVolume(it, volume) }
    fun moveSelectedVideo(toIndex: Int) = withSelection { editor.moveVideoClip(it, toIndex) }
    fun moveSelectedAudio(startMs: Long) = withSelection { editor.moveAudioClip(it, startMs) }
    fun undo() { editor.undo(); refresh() }
    fun redo() { editor.redo(); refresh() }
    fun renameProject(name: String) { _state.update { it.copy(projectName = name) } }

    /** Clears the message and a finished export's "completed" banner. */
    fun dismissMessage() {
        _state.update { it.copy(message = null, export = if (it.export == ExportUiState.Completed) ExportUiState.Idle else it.export) }
    }

    // ---------------- export ----------------
    fun onExportPresetSelected(preset: ExportPreset) { _state.update { it.copy(exportPreset = preset) } }

    fun onExportContainerSelected(container: ContainerFormat) {
        if (container in _state.value.exportContainers) _state.update { it.copy(exportContainer = container) }
    }

    /** Checks the project can be exported, then asks the UI for a destination. */
    fun onExportRequested() {
        if (_state.value.export is ExportUiState.Running) return
        viewModelScope.launch {
            val s = _state.value
            if (!OutputNames.isValid(s.projectName)) { setMessage(EditorMessage.InvalidProjectName); return@launch }
            val settings = ExportSettingsFactory(capabilities.repository()).create(s.exportPreset, s.exportContainer, referenceSize())
            if (settings == null) { setMessage(EditorMessage.NoExportEncoders); return@launch }
            val project = VideoProject(EXPORT_PROJECT_ID, s.projectName, editor.state.value.timeline, settings)
            (RenderPlanner.plan(library.forRender(project)) as? RenderPlanResult.Failure)?.let {
                setMessage(EditorMessage.ExportRejected(io.bsoft.bconverter.domain.usecase.ExportRejection.NotRenderable(it.error)))
                return@launch
            }
            pendingExport = project
            _events.trySend(EditorEvent.RequestExportDestination("${s.projectName.trim()}.${settings.container.extension}", settings.container.mimeType))
        }
    }

    /** [uri] is null when the save dialog was dismissed. */
    fun onExportDestinationChosen(uri: Uri?) {
        val project = pendingExport
        pendingExport = null
        if (uri == null || project == null) return
        val inputs = library.inputs()
        when (val start = exporter.start(library.forRender(project), uri, inputs)) {
            is ExportStart.Rejected -> setMessage(EditorMessage.ExportRejected(start.reason))
            is ExportStart.Started -> track(start.jobId, start.status, inputs)
        }
    }

    fun onCancelExport() { exportJobId?.let { jobs.cancel(it) } }

    private fun track(jobId: String, status: StateFlow<JobStatus>, inputs: List<ResolvedInput>) {
        exportJobId = jobId
        exportInputs = inputs
        _state.update { it.copy(export = ExportUiState.Running(0f)) }
        exportStatusJob = viewModelScope.launch {
            val terminal = status
                .onEach { s -> if (s is JobStatus.Running) _state.update { it.copy(export = ExportUiState.Running(s.progress.coerceIn(0f, 1f))) } }
                .first { it.isTerminal }
            jobs.finish(jobId)
            exportJobId = null
            exportInputs = emptyList()
            when (terminal) {
                JobStatus.Completed -> _state.update { it.copy(export = ExportUiState.Completed) }
                is JobStatus.Failed -> _state.update {
                    it.copy(
                        export = ExportUiState.Idle,
                        message = if (terminal.reason == JobFailure.SaveFailed) EditorMessage.ExportSaveFailed else EditorMessage.ExportFailed,
                    )
                }
                else -> _state.update { it.copy(export = ExportUiState.Idle) } // cancelled
            }
        }
    }

    /** The first clip's display size, which an export without a fixed size uses as its canvas. */
    private fun referenceSize(): Resolution? {
        val first = editor.state.value.timeline.videoClips.firstOrNull() ?: return null
        val stream = library.metadata(first.source)?.videoStreams?.firstOrNull() ?: return null
        return Resolution(stream.displayWidth, stream.displayHeight)
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

    private companion object { const val EXPORT_PROJECT_ID = "project" }
}
