package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExtractionPlannerTest {
    private val planner = SubtitleExtractionPlanner()
    private fun s(codec: String?) = SubtitleStream(2, "eng", codec)

    @Test fun text_streams_can_become_srt_ass_or_webvtt() {
        assertEquals(listOf(SubtitleOutput.SRT, SubtitleOutput.ASS, SubtitleOutput.WEBVTT), planner.outputsFor(s("subrip")))
        assertEquals(listOf(SubtitleOutput.SRT, SubtitleOutput.ASS, SubtitleOutput.WEBVTT), planner.outputsFor(s("mov_text")))
    }

    @Test fun image_streams_can_only_be_kept_as_images_until_ocr_exists() {
        assertEquals(listOf(SubtitleOutput.SUP, SubtitleOutput.MKS), planner.outputsFor(s("hdmv_pgs_subtitle")))
        assertEquals(listOf(SubtitleOutput.MKS), planner.outputsFor(s("dvd_subtitle")))
        assertEquals(listOf(SubtitleOutput.MKS), planner.outputsFor(s("dvb_subtitle")))
        assertTrue(planner.needsOcr(s("hdmv_pgs_subtitle"))); assertFalse(planner.needsOcr(s("subrip")))
    }

    @Test fun unknown_codecs_have_no_output() {
        assertEquals(emptyList<SubtitleOutput>(), planner.outputsFor(s("dvb_teletext")))
        assertNull(planner.defaultOutput(s(null)))
    }

    @Test fun the_default_output_matches_the_source_codec() {
        assertEquals(SubtitleOutput.SRT, planner.defaultOutput(s("subrip")))
        assertEquals(SubtitleOutput.SRT, planner.defaultOutput(s("mov_text")))
        assertEquals(SubtitleOutput.ASS, planner.defaultOutput(s("ass")))
        assertEquals(SubtitleOutput.WEBVTT, planner.defaultOutput(s("webvtt")))
        assertEquals(SubtitleOutput.SUP, planner.defaultOutput(s("hdmv_pgs_subtitle")))
        assertEquals(SubtitleOutput.MKS, planner.defaultOutput(s("dvd_subtitle")))
    }

    @Test fun copying_is_used_only_when_the_codec_already_matches() {
        assertTrue(planner.isCopy(s("subrip"), SubtitleOutput.SRT)); assertTrue(planner.isCopy(s("ass"), SubtitleOutput.ASS))
        assertTrue(planner.isCopy(s("hdmv_pgs_subtitle"), SubtitleOutput.SUP)); assertTrue(planner.isCopy(s("dvd_subtitle"), SubtitleOutput.MKS))
        assertFalse(planner.isCopy(s("mov_text"), SubtitleOutput.SRT)); assertFalse(planner.isCopy(s("ass"), SubtitleOutput.SRT))
    }

    @Test fun converting_styled_subtitles_to_plain_formats_is_reported() {
        assertTrue(planner.losesStyling(s("ass"), SubtitleOutput.SRT)); assertTrue(planner.losesStyling(s("ass"), SubtitleOutput.WEBVTT))
        assertFalse(planner.losesStyling(s("ass"), SubtitleOutput.ASS)); assertFalse(planner.losesStyling(s("subrip"), SubtitleOutput.SRT))
    }

    @Test fun settings_are_built_for_a_stream_and_output_or_refused() {
        val meta = MediaMetadata(1000, ContainerFormat.MKV, subtitleStreams = listOf(s("subrip"), s("hdmv_pgs_subtitle")))
        assertEquals(SubtitleExtractionSettings(0, SubtitleOutput.ASS), planner.settingsFor(meta, 0, SubtitleOutput.ASS))
        assertNull(planner.settingsFor(meta, 1, SubtitleOutput.SRT))      // images need OCR for text
        assertNull(planner.settingsFor(meta, 5, SubtitleOutput.SRT))      // no such stream
    }
}
