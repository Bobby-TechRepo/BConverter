package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecIdsTest {
    @Test fun video_names_map_to_codecs() {
        mapOf(
            "h264" to VideoCodec.H264, "hevc" to VideoCodec.HEVC, "vp8" to VideoCodec.VP8, "vp9" to VideoCodec.VP9,
            "av1" to VideoCodec.AV1, "mpeg4" to VideoCodec.MPEG4, "mpeg2video" to VideoCodec.MPEG2,
        ).forEach { (name, codec) -> assertEquals(name, codec, CodecIds.video(name)) }
    }
    @Test fun audio_names_map_to_codecs() {
        mapOf(
            "aac" to AudioCodec.AAC, "mp3" to AudioCodec.MP3, "opus" to AudioCodec.OPUS, "vorbis" to AudioCodec.VORBIS,
            "flac" to AudioCodec.FLAC, "ac3" to AudioCodec.AC3, "pcm_s16le" to AudioCodec.PCM, "pcm_s24le" to AudioCodec.PCM,
        ).forEach { (name, codec) -> assertEquals(name, codec, CodecIds.audio(name)) }
    }
    @Test fun matching_is_case_insensitive() = assertEquals(VideoCodec.H264, CodecIds.video("H264"))
    @Test fun unknown_or_missing_names_map_to_null() {
        assertNull(CodecIds.video("prores")); assertNull(CodecIds.video(null)); assertNull(CodecIds.audio("wmav2"))
    }
}
