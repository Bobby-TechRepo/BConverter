package io.bsoft.bconverter.media.device

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.VideoCodec

data class CodecInfo(val name: String, val supportedTypes: List<String>, val isEncoder: Boolean)

fun interface CodecInfoProvider { fun codecs(): List<CodecInfo> }

object CodecMimeTypes {
    private val MAP: Map<String, Codec> = mapOf(
        "video/avc" to VideoCodec.H264, "video/hevc" to VideoCodec.HEVC,
        "video/x-vnd.on2.vp8" to VideoCodec.VP8, "video/x-vnd.on2.vp9" to VideoCodec.VP9,
        "video/av01" to VideoCodec.AV1, "video/mp4v-es" to VideoCodec.MPEG4, "video/mpeg2" to VideoCodec.MPEG2,
        "audio/mp4a-latm" to AudioCodec.AAC, "audio/mpeg" to AudioCodec.MP3, "audio/opus" to AudioCodec.OPUS,
        "audio/vorbis" to AudioCodec.VORBIS, "audio/flac" to AudioCodec.FLAC, "audio/raw" to AudioCodec.PCM,
        "audio/ac3" to AudioCodec.AC3,
    )

    fun toCodec(mimeType: String): Codec? = MAP[mimeType.lowercase()]
}

/** Reports what the device's MediaCodec stack can decode/encode. */
class MediaCodecCapabilitySource(private val provider: CodecInfoProvider) {
    fun availability(): List<CodecAvailability> {
        val encoders = mutableSetOf<Codec>()
        val decoders = mutableSetOf<Codec>()
        for (info in provider.codecs()) {
            for (mime in info.supportedTypes) {
                val codec = CodecMimeTypes.toCodec(mime) ?: continue
                (if (info.isEncoder) encoders else decoders) += codec
            }
        }
        return (encoders + decoders).map { CodecAvailability(it, canDecode = it in decoders, canEncode = it in encoders) }
    }
}
