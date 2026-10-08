package io.bsoft.bconverter.domain.model

enum class ContainerFormat { MP4, MKV, THREE_GP, WEBM, MOV, AVI, MPEG_TS }

data class VideoStream(
    val index: Int,
    val codec: String?,
    val width: Int,
    val height: Int,
    val frameRate: Double?,
)

data class AudioStream(
    val index: Int,
    val codec: String?,
    val sampleRateHz: Int?,
    val channels: Int?,
)

data class SubtitleStream(val index: Int, val language: String?)

data class MediaMetadata(
    val durationMs: Long,
    val container: ContainerFormat?,
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream> = emptyList(),
    val subtitleStreams: List<SubtitleStream> = emptyList(),
) {
    init { require(durationMs >= 0) { "durationMs must be >= 0, was $durationMs" } }

    val hasVideo: Boolean get() = videoStreams.isNotEmpty()
    val hasAudio: Boolean get() = audioStreams.isNotEmpty()
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
}
