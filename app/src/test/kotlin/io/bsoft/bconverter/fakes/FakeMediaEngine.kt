package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.model.*
import io.bsoft.bconverter.media.api.*
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Deterministic in-memory MediaEngine for unit tests. Never touches FFmpeg. */
class FakeMediaEngine : MediaEngine {
    var probeResult: MediaMetadata = MediaMetadata(durationMs = 0, container = null)
    var processingResult: MediaProcessingResult = MediaProcessingResult.Success(File("fake-output"))

    val probedSources = mutableListOf<MediaSource>()
    val cancelledJobIds = mutableListOf<String>()
    private val progressFlows = mutableMapOf<String, MutableSharedFlow<MediaProcessingProgress>>()

    private fun flowFor(jobId: String) =
        progressFlows.getOrPut(jobId) { MutableSharedFlow(replay = 1, extraBufferCapacity = 16) }

    fun emitProgress(jobId: String, progress: MediaProcessingProgress) { flowFor(jobId).tryEmit(progress) }

    override suspend fun probe(source: MediaSource): MediaMetadata { probedSources += source; return probeResult }
    override suspend fun convert(request: ConversionRequest) = processingResult
    override suspend fun extractAudio(request: AudioExtractionRequest) = processingResult
    override suspend fun render(project: VideoProject, output: RenderOutput) = processingResult
    override suspend fun generateThumbnail(source: MediaSource, positionMs: Long) =
        Thumbnail(positionMs, File("fake-thumb.jpg"))
    override fun observeProgress(jobId: String): Flow<MediaProcessingProgress> = flowFor(jobId)
    override suspend fun cancel(jobId: String) { cancelledJobIds += jobId }
}
