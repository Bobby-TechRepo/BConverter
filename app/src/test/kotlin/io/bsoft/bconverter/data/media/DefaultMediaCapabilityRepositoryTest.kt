package io.bsoft.bconverter.data.media

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.VideoCodec.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMediaCapabilityRepositoryTest {
    private fun enc(vararg c: Codec) = c.map { CodecAvailability(it, canDecode = true, canEncode = true) }
    private fun dec(vararg c: Codec) = c.map { CodecAvailability(it, canDecode = true, canEncode = false) }
    private fun repo(vararg parts: List<CodecAvailability>) = DefaultMediaCapabilityRepository(parts.flatMap { it })

    // ---- video codecs ----
    @Test fun mp4_offers_only_valid_encodable_video_codecs() =
        assertEquals(listOf(H264, HEVC), repo(enc(H264, HEVC, VP8), dec(VP9)).getVideoCodecs(MP4))

    @Test fun decode_only_codecs_are_never_offered() =
        assertTrue(repo(dec(H264, HEVC)).getVideoCodecs(MP4).isEmpty())

    @Test fun results_follow_enum_declaration_order() {
        val r = repo(enc(AC3, AAC, MP3))
        assertEquals(listOf(AAC, MP3, AC3), r.getAudioCodecs(MKV))
    }

    // ---- audio codecs ----
    @Test fun webm_audio_is_limited_to_opus_and_vorbis() =
        assertEquals(listOf(OPUS, VORBIS), repo(enc(OPUS, VORBIS, AAC, MP3)).getAudioCodecs(WEBM))

    @Test fun absent_codecs_are_not_offered() = assertTrue(repo().getAudioCodecs(MKV).isEmpty())

    // ---- containers ----
    @Test fun containers_require_a_compatible_video_encoder() =
        assertEquals(listOf(MP4, MKV, THREE_GP, MOV, AVI, MPEG_TS), repo(enc(H264, AAC)).getContainers())

    @Test fun webm_appears_once_a_webm_video_encoder_exists() =
        assertTrue(WEBM in repo(enc(VP9)).getContainers())

    @Test fun audio_encoders_alone_yield_no_video_containers() = assertTrue(repo(enc(AAC)).getContainers().isEmpty())

    // ---- compatibility ----
    private val full = repo(enc(H264, HEVC, VP8, VP9, AAC, MP3, OPUS, FLAC, MPEG4))

    @Test fun h264_aac_in_mp4_is_compatible() = assertTrue(full.isCompatible(MP4, H264, AAC))
    @Test fun vp9_opus_in_webm_is_compatible() = assertTrue(full.isCompatible(WEBM, VP9, OPUS))
    @Test fun h264_in_webm_is_incompatible() = assertFalse(full.isCompatible(WEBM, H264, OPUS))
    @Test fun incompatible_audio_alone_fails_the_pair() = assertFalse(full.isCompatible(MP4, H264, FLAC))
    @Test fun hevc_in_3gp_is_incompatible() = assertFalse(full.isCompatible(THREE_GP, HEVC, AAC))
    @Test fun valid_pair_without_an_encoder_is_not_compatible() = assertFalse(repo(dec(H264), enc(AAC)).isCompatible(MP4, H264, AAC))
    @Test fun audio_only_selection_is_checked_on_its_own() { assertTrue(full.isCompatible(MP4, null, AAC)); assertFalse(full.isCompatible(WEBM, null, AAC)) }
    @Test fun video_only_selection_is_checked_on_its_own() = assertTrue(full.isCompatible(MKV, HEVC, null))
    @Test fun selecting_nothing_is_not_compatible() = assertFalse(full.isCompatible(MKV, null, null))

    // ---- capability model ----
    @Test fun duplicate_reports_are_merged_with_or_semantics() {
        val r = repo(dec(H264), enc(H264))
        assertEquals(listOf(H264), r.getVideoCodecs(MP4))
        val cap = r.getCapabilities().single { it.codec == H264 }
        assertTrue(cap.canDecode); assertTrue(cap.canEncode)
    }

    @Test fun every_known_codec_has_a_capability_entry_even_when_unavailable() {
        val caps = repo().getCapabilities()
        assertEquals(14, caps.size)
        assertTrue(caps.all { !it.canDecode && !it.canEncode })
    }

    @Test fun capability_lists_the_containers_that_can_hold_the_codec() =
        assertEquals(setOf(MKV, WEBM, MP4), repo(enc(VP9)).getCapabilities().single { it.codec == VP9 }.supportedContainers)

    @Test fun decode_and_encode_are_reported_separately() {
        val cap = repo(dec(HEVC)).getCapabilities().single { it.codec == HEVC }
        assertTrue(cap.canDecode); assertFalse(cap.canEncode)
    }

    // ---- rate control ----
    @Test fun crf_support_is_reported_per_codec() {
        val r = DefaultMediaCapabilityRepository(listOf(CodecAvailability(H264, true, true, supportsCrf = true), CodecAvailability(HEVC, true, true)))
        assertTrue(r.supportsCrf(H264)); assertFalse(r.supportsCrf(HEVC)); assertFalse(r.supportsCrf(VP9))
    }

    @Test fun crf_support_survives_merging_with_a_bitrate_only_report() {
        val r = DefaultMediaCapabilityRepository(listOf(CodecAvailability(H264, true, true, true), CodecAvailability(H264, true, true, false)))
        assertTrue(r.supportsCrf(H264))
    }

    @Test fun crf_support_requires_the_codec_to_be_encodable() {
        val r = DefaultMediaCapabilityRepository(listOf(CodecAvailability(H264, canDecode = true, canEncode = false, supportsCrf = true)))
        assertFalse(r.supportsCrf(H264))
    }
}
