package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.project.MissingSource
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.timeline.EditorState
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.usecase.ExportRejection

sealed interface EditorMessage {
    data class ProbeFailed(val failure: ProbeFailure) : EditorMessage
    data object NoVideoInFile : EditorMessage
    data object NoAudioInFile : EditorMessage
    data object UnknownDuration : EditorMessage
    data class EditFailed(val error: EditError) : EditorMessage
    data object InvalidProjectName : EditorMessage
    data object NoExportEncoders : EditorMessage
    data class ExportRejected(val reason: ExportRejection) : EditorMessage
    data object ExportFailed : EditorMessage
    data object ExportSaveFailed : EditorMessage
    data object ExportOverlaysFailed : EditorMessage
    /** The chosen picture could not be opened. */
    data object ImageUnreadable : EditorMessage
    data object ImageTooLarge : EditorMessage
    /** A clip is too long to reverse; [maxDurationMs] is what fits at this picture size. */
    data class ReverseTooLong(val maxDurationMs: Long) : EditorMessage
    data object ProjectNotFound : EditorMessage
    data object ProjectCorrupt : EditorMessage
    data object ProjectSaveFailed : EditorMessage
    data class SubtitleImportFailed(val reason: io.bsoft.bconverter.domain.usecase.SubtitleImportFailure) : EditorMessage
    /** The file was imported, but [count] parts of it could not be read or were simplified. */
    data class SubtitleImportedWithWarnings(val count: Int) : EditorMessage
    data class SubtitleEditRejected(val problem: io.bsoft.bconverter.domain.subtitle.EditProblem) : EditorMessage
    data class OverlayEditRejected(val problem: io.bsoft.bconverter.domain.overlay.OverlayProblem) : EditorMessage
}

/** Which centre lines the overlay being dragged is snapped to (drawn as guides while dragging). */
data class OverlayGuide(val vertical: Boolean = false, val horizontal: Boolean = false)

/** A cue of a subtitle track. */
data class CueRef(val trackId: String, val cueId: String)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val progress: Float) : ExportUiState
    data object Completed : ExportUiState
}

data class EditorUiState(
    val projectId: String = "",
    val projectName: String = DEFAULT_NAME,
    /** Timeline, selection, playhead and undo/redo availability. */
    val editor: EditorState = EditorState(),
    /** The timeline as the preview player sees it: sources are the prepared files (unprepared ones stay as they are). */
    val previewTimeline: Timeline = Timeline(),
    /** Sources of the project that can no longer be read, with the clips that use them. */
    val missingSources: List<MissingSource> = emptyList(),
    /** A file is being imported or a project is being prepared. */
    val busy: Boolean = false,
    val message: EditorMessage? = null,
    val export: ExportUiState = ExportUiState.Idle,
    val exportPreset: ExportPreset = ExportPreset.BALANCED,
    /** Not saved with the project: a new session starts at [ExportSize.AUTO]. */
    val exportSize: io.bsoft.bconverter.domain.model.ExportSize = io.bsoft.bconverter.domain.model.ExportSize.AUTO,
    val exportContainer: ContainerFormat = ContainerFormat.MP4,
    /** Containers the build can encode; empty until the capability check finishes. */
    val exportContainers: List<ContainerFormat> = emptyList(),
    /** Where the last finished export was saved, while its "done" banner is showing. */
    val savedExport: SavedOutput? = null,
    /** Subtitle tracks of the project, in lane order. Cue times are stored times; the track offset is applied when shown. */
    val subtitleTracks: List<io.bsoft.bconverter.domain.subtitle.SubtitleTrack> = emptyList(),
    val selectedCue: CueRef? = null,
    /** Text on the picture, back to front. */
    val textOverlays: List<io.bsoft.bconverter.domain.overlay.TextOverlay> = emptyList(),
    val selectedOverlayId: String? = null,
    /** Set while a drag is on a snap point, so the preview can draw the guide line. */
    val overlayGuide: OverlayGuide = OverlayGuide(),
    /** Width / height of the output picture (the first clip's display size); overlay positions are fractions of this frame. */
    val frameAspect: Float = 16f / 9f,
    /** The output shape and what fills the bars; saved with the project and undoable. */
    val canvas: io.bsoft.bconverter.domain.canvas.CanvasSettings = io.bsoft.bconverter.domain.canvas.CanvasSettings(),
    /** The selected video clip's picture size as shot (after the file's own rotation flag); crop shapes are measured on it. */
    val selectedSourceSize: io.bsoft.bconverter.domain.model.Resolution? = null,
    /** Step 24: the clips picked while selecting several; null when not in that mode. */
    val multiSelection: Set<String>? = null,
) {
    companion object { const val DEFAULT_NAME = "Untitled project" }
}

sealed interface EditorEvent {
    data class RequestExportDestination(val fileName: String, val mimeType: String) : EditorEvent
}
