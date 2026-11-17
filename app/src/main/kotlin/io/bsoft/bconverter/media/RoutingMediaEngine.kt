package io.bsoft.bconverter.media

import io.bsoft.bconverter.core.diagnostics.AppLog
import io.bsoft.bconverter.domain.render.HardwareRender
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.MediaEngine
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.media.transformer.HardwareRenderer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

/**
 * Step 19: renders a project on the device's encoders when [HardwareRender] says that gives the same result, and
 * with FFmpeg ([ffmpeg]) otherwise or when the hardware export fails. Everything else goes straight to [ffmpeg].
 * Progress of either path is published under the project id.
 */
class RoutingMediaEngine(
    private val ffmpeg: MediaEngine,
    private val hardware: HardwareRenderer?,
) : MediaEngine by ffmpeg {
    private val hardwareProgress = ConcurrentHashMap<String, MutableSharedFlow<MediaProcessingProgress>>()
    private val hardwareJobs = ConcurrentHashMap<String, Job>()

    private fun flowFor(jobId: String) =
        hardwareProgress.getOrPut(jobId) { MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    override fun observeProgress(jobId: String): Flow<MediaProcessingProgress> = merge(ffmpeg.observeProgress(jobId), flowFor(jobId))

    override suspend fun cancel(jobId: String) {
        hardwareJobs[jobId]?.cancel()
        ffmpeg.cancel(jobId)
    }

    override suspend fun render(project: VideoProject, output: RenderOutput): MediaProcessingResult {
        val renderer = hardware ?: return ffmpeg.render(project, output)
        val verdict = HardwareRender.check(project, hasOverlays = output.overlays.isNotEmpty(), hasSubtitles = !output.subtitles.isEmpty)
        if (verdict !is HardwareRender.Verdict.Eligible) return ffmpeg.render(project, output)

        val jobId = project.id
        val plan = verdict.plan
        val progress = flowFor(jobId)
        val result = try {
            coroutineScope {
                // A child job, so [cancel] stops the hardware export without cancelling the caller.
                val export = async {
                    renderer.render(plan, output.file) { fraction ->
                        val f = fraction.coerceIn(0f, 1f)
                        progress.tryEmit(MediaProcessingProgress((plan.durationMs * f).toLong(), plan.durationMs, f, null, null))
                    }
                }
                hardwareJobs[jobId] = export
                export.await()
            }
        } catch (e: CancellationException) {
            output.file.delete()
            // Cancelled through [cancel] while the caller is still running: report it like an FFmpeg cancel.
            if (currentCoroutineContext()[Job]?.isActive == true) return MediaProcessingResult.Cancelled
            throw e
        } catch (e: Exception) {
            MediaProcessingResult.Failure("Hardware export crashed: ${e.message}").also { AppLog.nonFatal(TAG, "Hardware export crashed", e) }
        } finally {
            hardwareJobs.remove(jobId)
            hardwareProgress.remove(jobId)
        }
        return when (result) {
            is MediaProcessingResult.Failure -> {
                AppLog.w(TAG, "Hardware export failed, using FFmpeg: ${result.message}")
                output.file.delete()
                ffmpeg.render(project, output)
            }
            else -> result
        }
    }

    private companion object { const val TAG = "Render" }
}
