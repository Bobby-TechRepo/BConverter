package io.bsoft.bconverter.media.device

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCodecCapabilitySourceTest {
    private fun source(vararg infos: CodecInfo) =
        MediaCodecCapabilitySource(object : CodecInfoProvider { override fun codecs() = infos.toList() })
    private fun List<CodecAvailability>.of(c: Codec) = single { it.codec == c }

    @Test fun avc_encoder_makes_h264_encodable() {
        val a = source(CodecInfo("c2.x.avc.encoder", listOf("video/avc"), isEncoder = true)).availability().of(VideoCodec.H264)
        assertTrue(a.canEncode); assertEquals(false, a.canDecode)
    }

    @Test fun decoder_only_codec_cannot_encode() {
        val a = source(CodecInfo("c2.x.mp3.decoder", listOf("audio/mpeg"), isEncoder = false)).availability().of(AudioCodec.MP3)
        assertTrue(a.canDecode); assertEquals(false, a.canEncode)
    }

    @Test fun encoder_and_decoder_entries_for_one_codec_are_merged() {
        val list = source(
            CodecInfo("enc", listOf("video/hevc"), true), CodecInfo("dec", listOf("video/hevc"), false),
        ).availability()
        assertEquals(1, list.size)
        assertTrue(list.of(VideoCodec.HEVC).canEncode && list.of(VideoCodec.HEVC).canDecode)
    }

    @Test fun one_codec_entry_can_serve_several_mime_types() {
        val list = source(CodecInfo("multi", listOf("video/avc", "video/hevc"), false)).availability()
        assertEquals(setOf<Codec>(VideoCodec.H264, VideoCodec.HEVC), list.map { it.codec }.toSet())
    }

    @Test fun mime_matching_is_case_insensitive() =
        assertEquals(VideoCodec.H264, source(CodecInfo("x", listOf("VIDEO/AVC"), false)).availability().single().codec)

    @Test fun unknown_mime_types_are_ignored() =
        assertTrue(source(CodecInfo("x", listOf("video/x-unknown", "image/heic"), true)).availability().isEmpty())

    @Test fun platform_mime_types_map_to_the_expected_codecs() {
        val expected = mapOf(
            "video/avc" to VideoCodec.H264, "video/hevc" to VideoCodec.HEVC, "video/x-vnd.on2.vp8" to VideoCodec.VP8,
            "video/x-vnd.on2.vp9" to VideoCodec.VP9, "video/av01" to VideoCodec.AV1, "video/mp4v-es" to VideoCodec.MPEG4,
            "video/mpeg2" to VideoCodec.MPEG2, "audio/mp4a-latm" to AudioCodec.AAC, "audio/mpeg" to AudioCodec.MP3,
            "audio/opus" to AudioCodec.OPUS, "audio/vorbis" to AudioCodec.VORBIS, "audio/flac" to AudioCodec.FLAC,
            "audio/raw" to AudioCodec.PCM, "audio/ac3" to AudioCodec.AC3,
        )
        expected.forEach { (mime, codec) -> assertEquals(mime, codec, CodecMimeTypes.toCodec(mime)) }
    }
}
