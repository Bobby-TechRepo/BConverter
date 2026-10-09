package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.VideoCodec

/** Maps an FFmpeg build's encoder/decoder listings onto domain codecs. */
object FfmpegCapabilities {
    private val NAMES: Map<String, Codec> = buildMap {
        listOf("h264", "libx264", "libopenh264", "h264_mediacodec").forEach { put(it, VideoCodec.H264) }
        listOf("hevc", "libx265", "hevc_mediacodec").forEach { put(it, VideoCodec.HEVC) }
        listOf("vp8", "libvpx").forEach { put(it, VideoCodec.VP8) }
        listOf("vp9", "libvpx-vp9").forEach { put(it, VideoCodec.VP9) }
        listOf("av1", "libdav1d", "libaom-av1", "libsvtav1", "librav1e", "av1_mediacodec").forEach { put(it, VideoCodec.AV1) }
        listOf("mpeg4", "libxvid").forEach { put(it, VideoCodec.MPEG4) }
        put("mpeg2video", VideoCodec.MPEG2)
        listOf("aac", "aac_fixed", "libfdk_aac").forEach { put(it, AudioCodec.AAC) }
        listOf("mp3", "mp3float", "libmp3lame").forEach { put(it, AudioCodec.MP3) }
        listOf("opus", "libopus").forEach { put(it, AudioCodec.OPUS) }
        listOf("vorbis", "libvorbis").forEach { put(it, AudioCodec.VORBIS) }
        put("flac", AudioCodec.FLAC)
        listOf("ac3", "ac3_fixed").forEach { put(it, AudioCodec.AC3) }
    }

    private fun codecFor(name: String): Codec? = NAMES[name] ?: if (name.startsWith("pcm_")) AudioCodec.PCM else null

    /** Experimental encoders are not offered; experimental decoders are harmless and kept. */
    fun fromListings(encoders: String, decoders: String): List<CodecAvailability> {
        val encodable = FfmpegCodecListParser.parse(encoders)
            .filter { !it.experimental && it.kind != FfmpegCodecKind.SUBTITLE }
            .mapNotNull { codecFor(it.name) }.toSet()
        val decodable = FfmpegCodecListParser.parse(decoders)
            .filter { it.kind != FfmpegCodecKind.SUBTITLE }
            .mapNotNull { codecFor(it.name) }.toSet()
        return (encodable + decodable).map { CodecAvailability(it, canDecode = it in decodable, canEncode = it in encodable) }
    }
}
