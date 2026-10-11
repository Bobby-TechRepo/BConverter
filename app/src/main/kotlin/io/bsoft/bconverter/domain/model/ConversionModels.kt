package io.bsoft.bconverter.domain.model

data class Resolution(val width: Int, val height: Int)

sealed interface VideoQuality {
    data class Crf(val value: Int) : VideoQuality
    data class Bitrate(val kbps: Int) : VideoQuality
}

/** CRF ranges per codec; null means the codec is rate-controlled by bitrate only. */
fun VideoCodec.crfRange(): IntRange? = when (this) {
    VideoCodec.H264, VideoCodec.HEVC -> 0..51
    VideoCodec.VP9, VideoCodec.AV1 -> 0..63
    else -> null
}

data class VideoSettings(
    val codec: VideoCodec,
    val quality: VideoQuality,
    val resolution: Resolution? = null,
    val frameRate: Double? = null,
    val profile: String? = null,
    val level: String? = null,
    val pixelFormat: String? = null,
)

data class AudioSettings(
    val codec: AudioCodec,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
)

sealed interface VideoPlan {
    data object Copy : VideoPlan
    data class Encode(val settings: VideoSettings) : VideoPlan
}

sealed interface AudioPlan {
    data object None : AudioPlan
    data object Copy : AudioPlan
    data class Encode(val settings: AudioSettings) : AudioPlan
}

data class ConversionSettings(val container: ContainerFormat, val video: VideoPlan, val audio: AudioPlan)

enum class ConversionPreset { SMALL_FILE, BALANCED, HIGH_QUALITY, ORIGINAL_QUALITY }

sealed interface ConversionIssue {
    data object SourceHasNoVideo : ConversionIssue
    data object SourceHasNoAudio : ConversionIssue
    data class CodecNotAllowedInContainer(val codec: Codec, val container: ContainerFormat) : ConversionIssue
    data class EncoderUnavailable(val codec: Codec) : ConversionIssue
    data class CrfNotSupported(val codec: VideoCodec) : ConversionIssue
    data class CrfOutOfRange(val codec: VideoCodec, val min: Int, val max: Int) : ConversionIssue
    data object InvalidVideoBitrate : ConversionIssue
    data object InvalidResolution : ConversionIssue
    data object InvalidFrameRate : ConversionIssue
    data object VideoCopyNotPossible : ConversionIssue
    data object AudioCopyNotPossible : ConversionIssue
    data class AudioBitrateNotAllowed(val codec: AudioCodec) : ConversionIssue
    data object InvalidAudioBitrate : ConversionIssue
    data object InvalidSampleRate : ConversionIssue
    data class InvalidChannels(val codec: AudioCodec) : ConversionIssue
    data object InvalidOutputName : ConversionIssue
}
