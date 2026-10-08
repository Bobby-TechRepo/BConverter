package io.bsoft.bconverter.media.api

import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import java.io.File
import kotlinx.coroutines.flow.Flow

// Request/result types are intentionally minimal; later phases extend them test-first.
data class ConversionRequest(val jobId: String, val source: MediaSource, val output: File)
data class AudioExtractionRequest(val jobId: String, val source: MediaSource, val output: File)
data class VideoProject(val id: String, val name: String)
data class RenderOutput(val file: File)
data class Thumbnail(val positionMs: Long, val file: File)

sealed interface MediaProcessingResult {
    data class Success(val output: File) : MediaProcessingResult
    data class Failure(val message: String) : MediaProcessingResult
}

data class MediaProcessingProgress(
    val processedDurationMs: Long,
    val totalDurationMs: Long?,
    val percentage: Float?,
    val speed: Double?,
    val estimatedRemainingMs: Long?,
)

/** The only seam between the app and FFmpeg/FFprobe (spec §9). No FFmpeg types here. */
interface MediaEngine {
    suspend fun probe(source: MediaSource): MediaMetadata
    suspend fun convert(request: ConversionRequest): MediaProcessingResult
    suspend fun extractAudio(request: AudioExtractionRequest): MediaProcessingResult
    suspend fun render(project: VideoProject, output: RenderOutput): MediaProcessingResult
    suspend fun generateThumbnail(source: MediaSource, positionMs: Long): Thumbnail
    fun observeProgress(jobId: String): Flow<MediaProcessingProgress>
    suspend fun cancel(jobId: String)
}
