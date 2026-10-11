package io.bsoft.bconverter.domain.model

/** Maps the codec identifiers reported by probing (e.g. "h264", "mpeg2video") onto domain codecs. */
object CodecIds {
    fun video(name: String?): VideoCodec? = when (name?.lowercase()) {
        "h264", "avc", "avc1" -> VideoCodec.H264
        "hevc", "h265" -> VideoCodec.HEVC
        "vp8" -> VideoCodec.VP8
        "vp9" -> VideoCodec.VP9
        "av1" -> VideoCodec.AV1
        "mpeg4" -> VideoCodec.MPEG4
        "mpeg2video", "mpeg2" -> VideoCodec.MPEG2
        else -> null
    }

    fun audio(name: String?): AudioCodec? {
        val n = name?.lowercase() ?: return null
        return when {
            n == "aac" -> AudioCodec.AAC
            n == "mp3" -> AudioCodec.MP3
            n == "opus" -> AudioCodec.OPUS
            n == "vorbis" -> AudioCodec.VORBIS
            n == "flac" -> AudioCodec.FLAC
            n == "ac3" -> AudioCodec.AC3
            n.startsWith("pcm_") -> AudioCodec.PCM
            else -> null
        }
    }
}
