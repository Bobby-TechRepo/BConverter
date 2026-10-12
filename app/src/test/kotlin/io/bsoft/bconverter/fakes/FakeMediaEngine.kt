package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.model.*
import io.bsoft.bconverter.media.api.*
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Deterministic in-memory MediaEngine for unit tests. Never touches FFmpeg. */
class FakeMediaEngine : MediaEngine {
    var probeResult: MediaMetadata = MediaMetadata(durationMs = 0, container = null)
    var processingResult: MediaProcessingResult = MediaProcessingResult.Success(File("fake-output"))

    /** When set, convert() suspends until completed. cancel() completes it with Cancelled, like a real engine. */
    var convertGate: CompletableDeferred<MediaProcessingResult>? = null
    var convertError: Throwable? = null

    /** Lets a test simulate FFmpeg writing the output file. */
    var onConvert: ((ConversionRequest) -> Unit)? = null
    var onExtract: ((AudioExtractionRequest) -> Unit)? = null

    val probedSources = mutableListOf<MediaSource>()
    val converted = mutableListOf<ConversionRequest>()
    val extracted = mutableListOf<AudioExtractionRequest>()
    val cancelledJobIds = mutableListOf<String>()
    private val progressFlows = mutableMapOf<String, MutableSharedFlow<MediaProcessingProgress>>()

    private fun flowFor(jobId: String) =
        progressFlows.getOrPut(jobId) { MutableSharedFlow(replay = 1, extraBufferCapacity = 16) }

    fun emitProgress(jobId: String, progress: MediaProcessingProgress) { flowFor(jobId).tryEmit(progress) }

    override suspend fun probe(source: MediaSource): MediaMetadata { probedSources += source; return probeResult }
    override suspend fun convert(request: ConversionRequest): MediaProcessingResult {
        converted += request
        onConvert?.invoke(request)
        convertError?.let { throw it }
        return convertGate?.await() ?: processingResult
    }
    override suspend fun extractAudio(request: AudioExtractionRequest): MediaProcessingResult {
        extracted += request
        onExtract?.invoke(request)
        convertError?.let { throw it }
        return convertGate?.await() ?: processingResult
    }
    override suspend fun render(project: VideoProject, output: RenderOutput) = processingResult
    override suspend fun generateThumbnail(source: MediaSource, positionMs: Long) =
        Thumbnail(positionMs, File("fake-thumb.jpg"))
    override fun observeProgress(jobId: String): Flow<MediaProcessingProgress> = flowFor(jobId)
    override suspend fun cancel(jobId: String) {
        cancelledJobIds += jobId
        convertGate?.complete(MediaProcessingResult.Cancelled)
    }
}
