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
import io.bsoft.bconverter.domain.subtitle.SubtitleExportPlanner
import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.timeline.UuidIdGenerator
import io.bsoft.bconverter.domain.timeline.VideoProject
import kotlinx.coroutines.flow.StateFlow

sealed interface ExportRejection {
    data class NotRenderable(val error: RenderError) : ExportRejection
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : ExportRejection
    /** A subtitle track cannot be carried by the chosen container. */
    data class SubtitlesNotSupported(val issues: List<io.bsoft.bconverter.domain.subtitle.SubtitleExportIssue>) : ExportRejection
    /** The subtitle files could not be prepared. */
    data object SubtitleFilesFailed : ExportRejection
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
    /** Where libass finds fonts when burning subtitles; evaluated when an export starts. */
    private val fontsDir: () -> String? = { SubtitleFileWriter.SYSTEM_FONTS },
) {
    fun start(project: VideoProject, destination: Uri, inputs: List<ResolvedInput>): ExportStart {
        (RenderPlanner.plan(project) as? RenderPlanResult.Failure)?.let {
            return ExportStart.Rejected(ExportRejection.NotRenderable(it.error))
        }
        (storage.check(StorageEstimator.estimateRender(project)) as? StorageCheck.Insufficient)?.let {
            return ExportStart.Rejected(ExportRejection.InsufficientStorage(it.requiredBytes, it.availableBytes))
        }
        val subtitlePlan = SubtitleExportPlanner.plan(project.subtitleTracks, project.export.container)
        if (subtitlePlan.blocking.isNotEmpty()) return ExportStart.Rejected(ExportRejection.SubtitlesNotSupported(subtitlePlan.blocking))
        val subtitles = try { SubtitleFileWriter(staging, fontsDir()).write(subtitlePlan) } catch (e: java.io.IOException) {
            return ExportStart.Rejected(ExportRejection.SubtitleFilesFailed)
        }
        val jobId = ids.next()
        val temp = staging.newFile("$jobId.${project.export.container.extension}")
        // The job id doubles as the project id so progress and cancellation never collide between renders.
        val job = ExportJob(jobId, project.name, ExportTask.Render(project.copy(id = jobId), temp, inputs, subtitles), destination)
        return ExportStart.Started(jobId, jobs.launch(job))
    }
}
