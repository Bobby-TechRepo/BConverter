package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngine
import io.bsoft.bconverter.media.api.MediaEngineException
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.media.api.Thumbnail
import io.bsoft.bconverter.media.api.VideoProject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** [MediaEngine] backed by FFmpeg/FFprobe through small seams, so everything but the native calls is unit-tested. */
class FfmpegMediaEngine(
    private val prober: MediaProber,
    private val resolver: InputResolver,
    private val executor: FfmpegExecutor,
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

    override suspend fun convert(request: ConversionRequest): MediaProcessingResult {
        val args = commandBuilder().buildConversionCommand(request).toArguments()
        val tracker = FfmpegProgressTracker(request.metadata.durationMs.takeIf { it > 0 })
        val progress = flowFor(request.jobId)
        try {
            return when (val result = executor.execute(request.jobId, args) { ms, speed -> progress.tryEmit(tracker.onStatistics(ms, speed)) }) {
                FfmpegExecResult.Success ->
                    if (request.outputFile.exists() && request.outputFile.length() > 0) MediaProcessingResult.Success(request.outputFile)
                    else MediaProcessingResult.Failure("FFmpeg reported success but produced no output file")
                is FfmpegExecResult.Failure -> MediaProcessingResult.Failure("FFmpeg exited with code ${result.exitCode}")
                FfmpegExecResult.Cancelled -> MediaProcessingResult.Cancelled
            }
        } finally {
            progressFlows.remove(request.jobId)
        }
    }

    override fun observeProgress(jobId: String): Flow<MediaProcessingProgress> = flowFor(jobId)

    override suspend fun cancel(jobId: String) = executor.cancel(jobId)

    // Implemented in later phases (6: audio extraction, 12: rendering, 8+: thumbnails).
    override suspend fun extractAudio(request: AudioExtractionRequest): MediaProcessingResult =
        throw UnsupportedOperationException("Audio extraction arrives in Phase 6")

    override suspend fun render(project: VideoProject, output: RenderOutput): MediaProcessingResult =
        throw UnsupportedOperationException("Rendering arrives in Phase 12")

    override suspend fun generateThumbnail(source: MediaSource, positionMs: Long): Thumbnail =
        throw UnsupportedOperationException("Thumbnails arrive with the timeline")
}
