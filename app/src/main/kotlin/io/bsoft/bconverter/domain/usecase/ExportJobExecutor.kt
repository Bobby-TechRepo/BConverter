package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.repository.PublishResult
import io.bsoft.bconverter.domain.overlay.OverlayLayer
import io.bsoft.bconverter.domain.overlay.OverlayLayerPreparer
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.MediaEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Runs one registered job to completion: engine → temp file → publish to the chosen destination.
 * Always removes the temp file; releases the staged input only when the owning screen has detached
 * (otherwise the screen keeps it for a retry). Cancellation (e.g. WorkManager cancel) stops the engine and cleans up.
 */
class ExportJobExecutor(
    private val engine: MediaEngine,
    private val resolver: InputResolver,
    private val publisher: OutputPublisher,
    private val registry: ExportRegistry,
    /** Draws text overlays for renders; without it overlays are not drawn. */
    private val overlays: OverlayLayerPreparer? = null,
) {
    suspend fun execute(jobId: String): JobStatus {
        val job = registry.job(jobId) ?: return JobStatus.Failed(JobFailure.Interrupted)
        var status: JobStatus = JobStatus.Cancelled
        try {
            status = coroutineScope {
                val session = ConversionSession(engine, resolver)
                val progress = launch {
                    session.state.collect { s -> if (s is ProcessingState.Running) registry.update(jobId, JobStatus.Running(s.progress)) }
                }
                try { run(job, session) } finally { progress.cancel() }
            }
            return status
        } finally {
            job.outputFile.delete()
            job.tempFiles.forEach { it.delete() }
            registry.update(jobId, status)
            if (registry.isDetached(jobId)) {
                job.inputs.forEach { resolver.release(it) }
                registry.remove(jobId)
            }
        }
    }

    /** The overlay pictures for [project]; empty when it has none; null if one cannot be drawn (the export must not silently lose text). */
    private suspend fun prepareOverlays(project: VideoProject): List<OverlayLayer>? {
        if (project.textOverlays.isEmpty()) return emptyList()
        val preparer = overlays ?: return null
        val canvas = (RenderPlanner.plan(project) as? RenderPlanResult.Success)?.plan?.canvas ?: return null
        return preparer.prepare(project.textOverlays, canvas.width, canvas.height)
    }

    private suspend fun run(job: ExportJob, session: ConversionSession): JobStatus {
        when (val task = job.task) {
            is ExportTask.Convert -> session.run(task.request, releaseInput = false)
            is ExportTask.Extract -> session.runExtraction(task.request, releaseInput = false)
            is ExportTask.ExtractSubtitle -> session.runSubtitleExtraction(task.request, releaseInput = false)
            is ExportTask.Render -> {
                val layers = prepareOverlays(task.project) ?: return JobStatus.Failed(JobFailure.OverlaysFailed)
                session.runRender(task.project, task.outputFile, task.inputs, releaseInput = false, subtitles = task.subtitles, overlays = layers)
            }
        }
        return when (session.state.value) {
            ProcessingState.Completed -> {
                registry.update(job.id, JobStatus.Publishing)
                if (publisher.publish(job.outputFile, job.destination) == PublishResult.Success) JobStatus.Completed
                else JobStatus.Failed(JobFailure.SaveFailed)
            }
            is ProcessingState.Failed -> JobStatus.Failed(JobFailure.ConversionFailed)
            else -> JobStatus.Cancelled
        }
    }
}
