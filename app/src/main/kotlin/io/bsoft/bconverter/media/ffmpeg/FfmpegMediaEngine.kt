package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.render.SegmentedRender
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngine
import io.bsoft.bconverter.media.api.MediaEngineException
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.media.api.Thumbnail
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** [MediaEngine] backed by FFmpeg/FFprobe through small seams, so everything but the native calls is unit-tested. */
class FfmpegMediaEngine(
    private val prober: MediaProber,
    private val resolver: InputResolver,
    private val executor: FfmpegExecutor,
    /** Projects with more video clips than this are rendered in parts (step 18). */
    private val maxClipsPerPart: Int = SegmentedRender.MAX_CLIPS_PER_PART,
    /** Suspending so it can wait for the installed build's encoder names. */
    private val commandBuilder: suspend () -> FfmpegCommandBuilder,
) : MediaEngine {
    private val progressFlows = ConcurrentHashMap<String, MutableSharedFlow<MediaProcessingProgress>>()

    private fun flowFor(jobId: String) =
        progressFlows.getOrPut(jobId) { MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    override suspend fun probe(source: MediaSource): MediaMetadata = when (val outcome = prober.probe(source)) {
        is ProbeOutcome.Success -> { resolver.release(outcome.input); outcome.metadata }
        is ProbeOutcome.Failure -> throw MediaEngineException(outcome.failure)
    }

    override suspend fun convert(request: ConversionRequest): MediaProcessingResult =
        execute(request.jobId, commandBuilder().buildConversionCommand(request).toArguments(), request.metadata.durationMs, request.outputFile)

    override suspend fun extractAudio(request: AudioExtractionRequest): MediaProcessingResult =
        execute(request.jobId, commandBuilder().buildAudioExtractionCommand(request).toArguments(), request.metadata.durationMs, request.outputFile)

    override suspend fun extractSubtitle(request: io.bsoft.bconverter.media.api.SubtitleExtractionRequest): MediaProcessingResult =
        execute(request.jobId, commandBuilder().buildSubtitleExtractionCommand(request).toArguments(), request.metadata.durationMs, request.outputFile)

    private suspend fun execute(jobId: String, args: List<String>, durationMs: Long, output: File): MediaProcessingResult {
        val tracker = FfmpegProgressTracker(durationMs.takeIf { it > 0 })
        val progress = flowFor(jobId)
        try {
            return runOnce(jobId, args, output) { ms, speed -> progress.tryEmit(tracker.onStatistics(ms, speed)) }
        } finally {
            progressFlows.remove(jobId)
            cancelled.remove(jobId)
        }
    }

    /** One FFmpeg run; success needs a non-empty [output]. */
    private suspend fun runOnce(jobId: String, args: List<String>, output: File, onStatistics: (Long, Double?) -> Unit): MediaProcessingResult =
        when (val result = executor.execute(jobId, args, onStatistics)) {
            FfmpegExecResult.Success ->
                if (output.exists() && output.length() > 0) MediaProcessingResult.Success(output)
                else MediaProcessingResult.Failure("FFmpeg reported success but produced no output file")
            is FfmpegExecResult.Failure -> MediaProcessingResult.Failure(
                "FFmpeg exited with code ${result.exitCode}" + (result.logTail?.let { "\n" + it.takeLast(FAILURE_LOG_CHARS) } ?: ""),
            )
            FfmpegExecResult.Cancelled -> MediaProcessingResult.Cancelled
        }

    override fun observeProgress(jobId: String): Flow<MediaProcessingProgress> = flowFor(jobId)

    /** Jobs cancelled while between two FFmpeg runs of a render in parts (nothing is running to be stopped then). */
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    override suspend fun cancel(jobId: String) {
        cancelled.add(jobId)
        executor.cancel(jobId)
    }

    /** The project id is the job id for progress and cancellation. Long projects are rendered in parts (step 18). */
    override suspend fun render(project: VideoProject, output: RenderOutput): MediaProcessingResult {
        val plan = (RenderPlanner.plan(project) as? RenderPlanResult.Success)?.plan
            ?: return MediaProcessingResult.Failure("The project cannot be rendered")
        val workDir = output.file.absoluteFile.parentFile
        val parts = workDir?.let { SegmentedRender.plan(project, plan, it, output.file.nameWithoutExtension, maxClipsPerPart) }
            ?: return execute(project.id, commandBuilder().buildRenderCommand(project, output).toArguments(), plan.durationMs, output.file)
        return renderInParts(project.id, parts, output)
    }

    /**
     * Renders each part, joins them without re-encoding, then runs the final pass over the joined file. Progress covers
     * the parts as the first half and the final pass as the second. Intermediate files are always deleted.
     */
    private suspend fun renderInParts(jobId: String, s: SegmentedRender.Plan, output: RenderOutput): MediaProcessingResult {
        cancelled.remove(jobId)
        val tracker = FfmpegProgressTracker((s.durationMs * 2).takeIf { it > 0 })
        val progress = flowFor(jobId)
        val builder = commandBuilder()
        try {
            var done = 0L
            for ((n, part) in s.parts.withIndex()) {
                if (jobId in cancelled) return MediaProcessingResult.Cancelled
                val offset = done
                val args = builder.buildRenderCommand(part.project, RenderOutput(part.file, keepSoundTrack = s.partsHaveSound)).toArguments()
                val result = runOnce(jobId, args, part.file) { ms, speed -> progress.tryEmit(tracker.onStatistics(offset + ms.coerceAtMost(part.durationMs), speed)) }
                if (result !is MediaProcessingResult.Success) return result.inStage("part ${n + 1} of ${s.parts.size}")
                done += part.durationMs
            }
            if (jobId in cancelled) return MediaProcessingResult.Cancelled
            s.listFile.writeText(SegmentedRender.concatList(s.parts.map { it.file }))
            val joined = runOnce(jobId, FfmpegConcat.command(s.listFile, s.joinedFile).toArguments(), s.joinedFile) { _, _ -> }
            if (joined !is MediaProcessingResult.Success) return joined.inStage("joining the parts")
            // The parts are no longer needed; free their space before the final pass writes the export.
            s.parts.forEach { it.file.delete() }
            if (jobId in cancelled) return MediaProcessingResult.Cancelled
            val finalArgs = builder.buildRenderCommand(s.finalProject, output).toArguments()
            return runOnce(jobId, finalArgs, output.file) { ms, speed -> progress.tryEmit(tracker.onStatistics(s.durationMs + ms, speed)) }
                .inStage("final pass")
        } finally {
            progressFlows.remove(jobId)
            cancelled.remove(jobId)
            s.parts.forEach { it.file.delete() }
            s.joinedFile.delete()
            s.listFile.delete()
        }
    }

    private fun MediaProcessingResult.inStage(stage: String): MediaProcessingResult =
        if (this is MediaProcessingResult.Failure) MediaProcessingResult.Failure("Rendering failed in the $stage: $message") else this

    // Arrives with the timeline UI.
    override suspend fun generateThumbnail(source: MediaSource, positionMs: Long): Thumbnail =
        throw UnsupportedOperationException("Thumbnails arrive with the timeline")
}

/** How much of FFmpeg's log a failure result carries (the full tail is in the failure log file). */
private const val FAILURE_LOG_CHARS = 2_000
