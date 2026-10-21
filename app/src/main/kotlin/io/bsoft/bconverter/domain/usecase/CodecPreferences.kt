package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.VideoCodec

/** Which encoder to pick first when several are valid for a container (best default first). */
object CodecPreferences {
    val VIDEO = listOf(
        VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9, VideoCodec.AV1, VideoCodec.VP8, VideoCodec.MPEG4, VideoCodec.MPEG2,
    )
    val AUDIO = listOf(
        AudioCodec.AAC, AudioCodec.OPUS, AudioCodec.MP3, AudioCodec.AC3, AudioCodec.VORBIS, AudioCodec.FLAC, AudioCodec.PCM,
    )
}
