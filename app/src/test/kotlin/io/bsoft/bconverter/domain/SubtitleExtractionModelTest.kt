package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleExtractionModelTest {
    @Test fun codecs_are_classified_as_text_image_or_unsupported() {
        listOf("subrip", "srt", "ass", "ssa", "webvtt", "mov_text", "ttml", "text", "subviewer", "microdvd", "SUBRIP").forEach {
            assertEquals(it, SubtitleKind.TEXT, SubtitleCodecInfo.kind(it))
        }
        listOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub").forEach { assertEquals(it, SubtitleKind.IMAGE, SubtitleCodecInfo.kind(it)) }
        listOf("dvb_teletext", "eia_608", "something_new", null).forEach { assertEquals("$it", SubtitleKind.UNSUPPORTED, SubtitleCodecInfo.kind(it)) }
    }

    @Test fun a_stream_knows_its_kind() {
        assertEquals(SubtitleKind.IMAGE, SubtitleStream(3, "eng", "hdmv_pgs_subtitle").kind)
        assertEquals(SubtitleKind.TEXT, SubtitleStream(3, null, "subrip").kind)
    }

    @Test fun outputs_know_their_files_and_encoders() {
        assertEquals("srt", SubtitleOutput.SRT.extension); assertEquals("srt", SubtitleOutput.SRT.encoder)
        assertEquals("webvtt", SubtitleOutput.WEBVTT.muxer); assertEquals("vtt", SubtitleOutput.WEBVTT.extension)
        assertEquals("sup", SubtitleOutput.SUP.muxer); assertEquals(null, SubtitleOutput.SUP.encoder)
        assertEquals("matroska", SubtitleOutput.MKS.muxer); assertEquals("mks", SubtitleOutput.MKS.extension)
    }
}
