package io.bsoft.bconverter.domain.model

sealed interface Codec

enum class VideoCodec : Codec { H264, HEVC, VP8, VP9, AV1, MPEG4, MPEG2 }

enum class AudioCodec : Codec { AAC, MP3, OPUS, VORBIS, FLAC, PCM, AC3 }

/** What one source (FFmpeg build, device MediaCodec, …) says it can do with a codec. */
data class CodecAvailability(val codec: Codec, val canDecode: Boolean, val canEncode: Boolean)

data class CodecCapability(
    val codec: Codec,
    val canDecode: Boolean,
    val canEncode: Boolean,
    val supportedContainers: Set<ContainerFormat>,
)
