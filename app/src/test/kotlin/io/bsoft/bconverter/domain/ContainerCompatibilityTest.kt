package io.bsoft.bconverter.domain.model

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.VideoCodec.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerCompatibilityTest {
    private fun ok(c: ContainerFormat, codec: Codec) = assertTrue("$c should support $codec", ContainerCompatibility.supports(c, codec))
    private fun no(c: ContainerFormat, codec: Codec) = assertFalse("$c should not support $codec", ContainerCompatibility.supports(c, codec))

    @Test fun mkv_accepts_every_known_codec() {
        VideoCodec.entries.forEach { ok(MKV, it) }; AudioCodec.entries.forEach { ok(MKV, it) }
    }

    @Test fun webm_is_limited_to_vp8_vp9_av1_and_opus_vorbis() {
        assertEquals(setOf(VP8, VP9, AV1), ContainerCompatibility.videoCodecs(WEBM))
        assertEquals(setOf(OPUS, VORBIS), ContainerCompatibility.audioCodecs(WEBM))
    }

    @Test fun mp4_supports_the_mainstream_codecs() {
        listOf<Codec>(H264, HEVC, AV1, VP9, MPEG4, AAC, MP3, AC3, OPUS).forEach { ok(MP4, it) }
    }

    @Test fun mp4_rejects_vp8_vorbis_and_pcm() { listOf<Codec>(VP8, VORBIS, PCM, MPEG2).forEach { no(MP4, it) } }
    @Test fun webm_rejects_h264_and_aac() { no(WEBM, H264); no(WEBM, AAC) }
    @Test fun threegp_supports_h264_mpeg4_aac_only() {
        listOf<Codec>(H264, MPEG4, AAC).forEach { ok(THREE_GP, it) }
        listOf<Codec>(HEVC, VP9, MP3, OPUS).forEach { no(THREE_GP, it) }
    }
    @Test fun mov_supports_h264_hevc_aac_pcm_but_not_vp9() {
        listOf<Codec>(H264, HEVC, AAC, PCM).forEach { ok(MOV, it) }; no(MOV, VP9)
    }
    @Test fun avi_supports_mpeg4_h264_mp3_but_not_opus() {
        listOf<Codec>(MPEG4, H264, MP3, AC3, PCM).forEach { ok(AVI, it) }; no(AVI, OPUS)
    }
    @Test fun mpeg_ts_supports_mpeg2_h264_hevc_but_not_vp9() {
        listOf<Codec>(MPEG2, H264, HEVC, AAC, AC3).forEach { ok(MPEG_TS, it) }; no(MPEG_TS, VP9)
    }
    @Test fun every_container_has_at_least_one_video_and_audio_codec() = ContainerFormat.entries.forEach {
        assertTrue(ContainerCompatibility.videoCodecs(it).isNotEmpty())
        assertTrue(ContainerCompatibility.audioCodecs(it).isNotEmpty())
    }
}
