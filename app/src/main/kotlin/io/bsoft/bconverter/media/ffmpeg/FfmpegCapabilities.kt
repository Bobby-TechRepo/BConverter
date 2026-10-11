package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.VideoCodec

/** Maps an FFmpeg build's encoder/decoder listings onto domain codecs, rate-control support and encoder names. */
object FfmpegCapabilities {
    /** Preferred encoder per codec, best first. CRF-capable software encoders precede bitrate-only ones. */
    private val ENCODER_PRIORITY: Map<Codec, List<String>> = mapOf(
        VideoCodec.H264 to listOf("libx264", "libopenh264", "h264_mediacodec"),
        VideoCodec.HEVC to listOf("libx265", "libkvazaar", "hevc_mediacodec"),
        VideoCodec.VP8 to listOf("libvpx"),
        VideoCodec.VP9 to listOf("libvpx-vp9"),
        VideoCodec.AV1 to listOf("libsvtav1", "libaom-av1", "librav1e", "av1_mediacodec"),
        VideoCodec.MPEG4 to listOf("mpeg4", "libxvid"),
        VideoCodec.MPEG2 to listOf("mpeg2video"),
        AudioCodec.AAC to listOf("aac", "libfdk_aac"),
        AudioCodec.MP3 to listOf("libmp3lame", "libshine"),
        AudioCodec.OPUS to listOf("libopus", "opus"),
        AudioCodec.VORBIS to listOf("libvorbis", "vorbis"),
        AudioCodec.FLAC to listOf("flac"),
        AudioCodec.PCM to listOf("pcm_s16le"),
        AudioCodec.AC3 to listOf("ac3", "ac3_fixed"),
    )

    private val CRF_ENCODERS = setOf("libx264", "libx265", "libvpx-vp9", "libsvtav1", "libaom-av1", "librav1e")

    private val DECODER_NAMES: Map<String, Codec> = buildMap {
        listOf("h264", "libopenh264", "h264_mediacodec").forEach { put(it, VideoCodec.H264) }
        listOf("hevc", "hevc_mediacodec").forEach { put(it, VideoCodec.HEVC) }
        listOf("vp8", "libvpx").forEach { put(it, VideoCodec.VP8) }
        listOf("vp9", "libvpx-vp9").forEach { put(it, VideoCodec.VP9) }
        listOf("av1", "libdav1d", "libaom-av1", "av1_mediacodec").forEach { put(it, VideoCodec.AV1) }
        put("mpeg4", VideoCodec.MPEG4)
        put("mpeg2video", VideoCodec.MPEG2)
        listOf("aac", "aac_fixed").forEach { put(it, AudioCodec.AAC) }
        listOf("mp3", "mp3float").forEach { put(it, AudioCodec.MP3) }
        listOf("opus", "libopus").forEach { put(it, AudioCodec.OPUS) }
        listOf("vorbis", "libvorbis").forEach { put(it, AudioCodec.VORBIS) }
        put("flac", AudioCodec.FLAC)
        listOf("ac3", "ac3_fixed").forEach { put(it, AudioCodec.AC3) }
    }

    private data class Usable(val name: String, val codec: Codec)

    private fun encoderCodec(name: String): Codec? =
        ENCODER_PRIORITY.entries.firstOrNull { name in it.value }?.key ?: if (name.startsWith("pcm_")) AudioCodec.PCM else null

    private fun decoderCodec(name: String): Codec? = DECODER_NAMES[name] ?: if (name.startsWith("pcm_")) AudioCodec.PCM else null

    /**
     * Stable, non-subtitle encoders we know. MediaCodec wrappers are listed by FFmpeg whether or not the
     * device has the hardware, so when [deviceEncoders] is given they only count if the device has a matching encoder.
     */
    private fun usableEncoders(encoders: String, deviceEncoders: Set<Codec>?): List<Usable> =
        FfmpegCodecListParser.parse(encoders)
            .filter { !it.experimental && it.kind != FfmpegCodecKind.SUBTITLE }
            .mapNotNull { entry ->
                val codec = encoderCodec(entry.name) ?: return@mapNotNull null
                if (entry.name.endsWith("_mediacodec") && deviceEncoders != null && codec !in deviceEncoders) null
                else Usable(entry.name, codec)
            }

    fun fromListings(encoders: String, decoders: String, deviceEncoders: Set<Codec>? = null): List<CodecAvailability> {
        val usable = usableEncoders(encoders, deviceEncoders)
        val encodable = usable.map { it.codec }.toSet()
        val crf = usable.filter { it.name in CRF_ENCODERS }.map { it.codec }.toSet()
        val decodable = FfmpegCodecListParser.parse(decoders)
            .filter { it.kind != FfmpegCodecKind.SUBTITLE }
            .mapNotNull { decoderCodec(it.name) }.toSet()
        return (encodable + decodable).map {
            CodecAvailability(it, canDecode = it in decodable, canEncode = it in encodable, supportsCrf = it in crf)
        }
    }

    /** The encoder to use for each codec in this build; codecs without an installed encoder keep their default name. */
    fun encoderNames(encoders: String, deviceEncoders: Set<Codec>? = null): EncoderNames {
        val usable = usableEncoders(encoders, deviceEncoders)
        fun pick(codec: Codec): String? =
            ENCODER_PRIORITY[codec]?.firstOrNull { p -> usable.any { it.name == p && it.codec == codec } }
                ?: if (codec == AudioCodec.PCM) usable.firstOrNull { it.codec == codec }?.name else null
        return EncoderNames(
            video = EncoderNames.Default.video.mapValues { (codec, default) -> pick(codec) ?: default },
            audio = EncoderNames.Default.audio.mapValues { (codec, default) -> pick(codec) ?: default },
        )
    }
}
