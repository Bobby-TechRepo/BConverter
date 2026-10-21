package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.timeline.EditorState
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
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val progress: Float) : ExportUiState
    data object Completed : ExportUiState
}

data class EditorUiState(
    val projectName: String = DEFAULT_NAME,
    /** Timeline, selection, playhead and undo/redo availability. */
    val editor: EditorState = EditorState(),
    /** A file is being imported. */
    val busy: Boolean = false,
    val message: EditorMessage? = null,
    val export: ExportUiState = ExportUiState.Idle,
    val exportPreset: ExportPreset = ExportPreset.BALANCED,
    val exportContainer: ContainerFormat = ContainerFormat.MP4,
    /** Containers the build can encode; empty until the capability check finishes. */
    val exportContainers: List<ContainerFormat> = emptyList(),
) {
    companion object { const val DEFAULT_NAME = "Untitled project" }
}

sealed interface EditorEvent {
    data class RequestExportDestination(val fileName: String, val mimeType: String) : EditorEvent
}
