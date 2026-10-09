package io.bsoft.bconverter.domain.model

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.VideoCodec.*

/**
 * Which codecs each container can hold. Deliberately conservative: only combinations that are
 * standard or widely playable. Verified against real muxers by the FFmpeg integration tests (spec §31).
 */
object ContainerCompatibility {
    fun videoCodecs(container: ContainerFormat): Set<VideoCodec> = when (container) {
        MP4 -> setOf(H264, HEVC, AV1, VP9, MPEG4)
        MKV -> VideoCodec.entries.toSet()
        THREE_GP -> setOf(H264, MPEG4)
        WEBM -> setOf(VP8, VP9, AV1)
        MOV -> setOf(H264, HEVC, MPEG4)
        AVI -> setOf(H264, MPEG4)
        MPEG_TS -> setOf(MPEG2, H264, HEVC)
    }

    fun audioCodecs(container: ContainerFormat): Set<AudioCodec> = when (container) {
        MP4 -> setOf(AAC, MP3, AC3, OPUS)
        MKV -> AudioCodec.entries.toSet()
        THREE_GP -> setOf(AAC)
        WEBM -> setOf(OPUS, VORBIS)
        MOV -> setOf(AAC, MP3, AC3, PCM)
        AVI -> setOf(MP3, AC3, PCM)
        MPEG_TS -> setOf(AAC, MP3, AC3)
    }

    fun supports(container: ContainerFormat, codec: Codec): Boolean = when (codec) {
        is VideoCodec -> codec in videoCodecs(container)
        is AudioCodec -> codec in audioCodecs(container)
    }
}
