package io.bsoft.bconverter.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleModelTest {
    private fun cue(id: String, start: Long, end: Long, text: String = "x") = SubtitleCue(id, start, end, text)

    @Test fun a_cue_needs_a_non_negative_start_and_an_end_after_it() {
        assertTrue(runCatching { cue("a", -1, 10) }.isFailure)
        assertTrue(runCatching { cue("a", 10, 10) }.isFailure)
        assertTrue(runCatching { cue("a", 10, 5) }.isFailure)
        assertEquals(1L, cue("a", 0, 1).durationMs)
    }

    @Test fun timing_validation_reports_why_an_interval_is_invalid() {
        assertEquals(null, SubtitleTiming.validate(0, 1))
        assertEquals(TimingError.NegativeStart, SubtitleTiming.validate(-5, 10))
        assertEquals(TimingError.EndNotAfterStart, SubtitleTiming.validate(10, 10))
        assertEquals(TimingError.TooShort, SubtitleTiming.validate(0, 5, minDurationMs = 100))
    }

    @Test fun an_offset_moves_every_cue_and_cues_pushed_before_zero_are_dropped_or_trimmed() {
        val track = SubtitleTrack("t", "T", cues = listOf(cue("a", 1_000, 2_000), cue("b", 5_000, 6_000)), offsetMs = -1_500)
        val effective = track.effectiveCues()
        assertEquals(listOf("a", "b"), effective.map { it.id })
        assertEquals(0L to 500L, effective[0].startMs to effective[0].endMs)   // trimmed at zero
        assertEquals(3_500L to 4_500L, effective[1].startMs to effective[1].endMs)
        val gone = track.copy(offsetMs = -2_500).effectiveCues()
        assertEquals(listOf("b"), gone.map { it.id })
    }

    @Test fun a_positive_offset_delays_all_cues() {
        val track = SubtitleTrack("t", "T", cues = listOf(cue("a", 1_000, 2_000)), offsetMs = 2_000)
        assertEquals(3_000L, track.effectiveCues().single().startMs)
    }

    @Test fun effective_cues_come_sorted_by_start_time() {
        val track = SubtitleTrack("t", "T", cues = listOf(cue("b", 5_000, 6_000), cue("a", 1_000, 2_000)))
        assertEquals(listOf("a", "b"), track.effectiveCues().map { it.id })
    }

    @Test fun alignments_know_their_row_and_column() {
        assertEquals(SubtitleAlignment.BOTTOM_CENTER, SubtitleAlignment.fromNumpad(2))
        assertEquals(SubtitleAlignment.TOP_LEFT, SubtitleAlignment.fromNumpad(7))
        assertEquals(null, SubtitleAlignment.fromNumpad(0))
        assertTrue(SubtitleAlignment.TOP_RIGHT.isTop); assertFalse(SubtitleAlignment.TOP_RIGHT.isBottom)
    }

    @Test fun formats_know_their_extension_and_mime_type() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("movie.en.SRT"))
        assertEquals(SubtitleFormat.WEBVTT, SubtitleFormat.fromFileName("a.vtt"))
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromFileName("a.ass"))
        assertEquals(SubtitleFormat.TTML, SubtitleFormat.fromFileName("a.ttml"))
        assertEquals(SubtitleFormat.TTML, SubtitleFormat.fromFileName("a.dfxp"))
        assertEquals(null, SubtitleFormat.fromFileName("a.txt"))
        assertEquals("srt", SubtitleFormat.SRT.extension)
    }
}
