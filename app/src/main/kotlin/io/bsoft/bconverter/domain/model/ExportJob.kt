package io.bsoft.bconverter.domain.model

import android.net.Uri
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import java.io.File

sealed interface ExportTask {
    data class Convert(val request: ConversionRequest) : ExportTask
    data class Extract(val request: AudioExtractionRequest) : ExportTask
    /** Renders [project] into [outputFile]; [inputs] are the staged copies of its sources, owned by the job. */
    data class Render(val project: VideoProject, val outputFile: File, val inputs: List<ResolvedInput>) : ExportTask
}

/** A unit of background work: run the task into a temp file, then save it to [destination]. */
data class ExportJob(val id: String, val title: String, val task: ExportTask, val destination: Uri) {
    val inputs: List<ResolvedInput>
        get() = when (task) {
            is ExportTask.Convert -> listOf(task.request.input)
            is ExportTask.Extract -> listOf(task.request.input)
            is ExportTask.Render -> task.inputs
        }
    val outputFile: File
        get() = when (task) {
            is ExportTask.Convert -> task.request.outputFile
            is ExportTask.Extract -> task.request.outputFile
            is ExportTask.Render -> task.outputFile
        }
}

enum class JobFailure {
    ConversionFailed, SaveFailed,
    /** The process was restarted and no longer knows the job (jobs cannot resume after process death). */
    Interrupted,
}

sealed interface JobStatus {
    data object Queued : JobStatus
    data class Running(val progress: Float) : JobStatus
    data object Publishing : JobStatus
    data object Completed : JobStatus
    data class Failed(val reason: JobFailure) : JobStatus
    data object Cancelled : JobStatus

    val isTerminal: Boolean get() = this is Completed || this is Failed || this is Cancelled
}
