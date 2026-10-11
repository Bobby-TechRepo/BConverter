package io.bsoft.bconverter.domain.model

enum class ContainerFormat(val extension: String) {
    MP4("mp4"), MKV("mkv"), THREE_GP("3gp"), WEBM("webm"), MOV("mov"), AVI("avi"), MPEG_TS("ts"),
}

data class VideoStream(
    val index: Int,
    val codec: String?,
    val width: Int,
    val height: Int,
    val frameRate: Double?,
    val bitrateBps: Long? = null,
    /** Clockwise rotation to apply for display: 0, 90, 180 or 270. */
    val rotationDegrees: Int = 0,
    val pixelFormat: String? = null,
) {
    init { require(rotationDegrees in VALID_ROTATIONS) { "rotation must be 0/90/180/270, was $rotationDegrees" } }

    private val quarterTurn get() = rotationDegrees == 90 || rotationDegrees == 270
    val displayWidth: Int get() = if (quarterTurn) height else width
    val displayHeight: Int get() = if (quarterTurn) width else height

    private companion object { val VALID_ROTATIONS = setOf(0, 90, 180, 270) }
}

data class AudioStream(
    val index: Int,
    val codec: String?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val bitrateBps: Long? = null,
    val language: String? = null,
)

data class SubtitleStream(val index: Int, val language: String?, val codec: String? = null)

data class MediaMetadata(
    val durationMs: Long,
    val container: ContainerFormat?,
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream> = emptyList(),
    val subtitleStreams: List<SubtitleStream> = emptyList(),
    val sizeBytes: Long? = null,
    val bitrateBps: Long? = null,
) {
    init { require(durationMs >= 0) { "durationMs must be >= 0, was $durationMs" } }

    val hasVideo: Boolean get() = videoStreams.isNotEmpty()
    val hasAudio: Boolean get() = audioStreams.isNotEmpty()
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
    val streamCount: Int get() = videoStreams.size + audioStreams.size + subtitleStreams.size
}
