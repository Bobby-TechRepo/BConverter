package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.render.RenderError
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.timeline.UuidIdGenerator
import io.bsoft.bconverter.domain.timeline.VideoProject
import kotlinx.coroutines.flow.StateFlow

sealed interface ExportRejection {
    data class NotRenderable(val error: RenderError) : ExportRejection
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : ExportRejection
}

sealed interface ExportStart {
    data class Started(val jobId: String, val status: StateFlow<JobStatus>) : ExportStart
    data class Rejected(val reason: ExportRejection) : ExportStart
}

/**
 * Starts the background render of a project: checks it can be planned and will fit, then queues a render job that
 * writes to a temp file and saves to [start]'s destination. [inputs] are the staged copies of the project's sources;
 * the job releases them when it finishes if the screen that owns them has gone.
 */
class ProjectExporter(
    private val staging: StagingDirectory,
    private val storage: StorageGuard,
    private val jobs: ExportJobLauncher,
    private val ids: IdGenerator = UuidIdGenerator,
) {
    fun start(project: VideoProject, destination: Uri, inputs: List<ResolvedInput>): ExportStart {
        (RenderPlanner.plan(project) as? RenderPlanResult.Failure)?.let {
            return ExportStart.Rejected(ExportRejection.NotRenderable(it.error))
        }
        (storage.check(StorageEstimator.estimateRender(project)) as? StorageCheck.Insufficient)?.let {
            return ExportStart.Rejected(ExportRejection.InsufficientStorage(it.requiredBytes, it.availableBytes))
        }
        val jobId = ids.next()
        val temp = staging.newFile("$jobId.${project.export.container.extension}")
        // The job id doubles as the project id so progress and cancellation never collide between renders.
        val job = ExportJob(jobId, project.name, ExportTask.Render(project.copy(id = jobId), temp, inputs), destination)
        return ExportStart.Started(jobId, jobs.launch(job))
    }
}
