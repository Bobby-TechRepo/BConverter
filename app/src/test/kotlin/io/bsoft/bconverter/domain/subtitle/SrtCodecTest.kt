package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.fakes.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtCodecTest {
    private fun parse(text: String) = SubtitleCodecs.parse(text, SubtitleFormat.SRT, ids = SequentialIds())
    private fun ok(text: String) = (parse(text) as SubtitleParseResult.Parsed)

    @Test fun cues_are_read_with_times_and_multiline_text() {
        val r = ok("1\n00:00:01,000 --> 00:00:02,500\nHello\nworld\n\n2\n01:02:03,004 --> 01:02:04,000\nBye\n")
        assertEquals(2, r.track.cues.size)
        assertEquals(SubtitleCue("id-2", 1_000, 2_500, "Hello\nworld"), r.track.cues[0])
        assertEquals(3_723_004L, r.track.cues[1].startMs)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun windows_line_endings_a_byte_order_mark_and_missing_indexes_are_tolerated() {
        val r = ok("﻿00:00:01,000 --> 00:00:02,000\r\nOne\r\n\r\n00:00:03.000 --> 00:00:04.000\r\nTwo\r\n")
        assertEquals(listOf("One", "Two"), r.track.cues.map { it.text })
    }

    @Test fun unicode_and_formatting_tags_survive() {
        val r = ok("1\n00:00:00,000 --> 00:00:01,000\n<i>こんにちは 😀</i>\nÉtè\n")
        assertEquals("<i>こんにちは 😀</i>\nÉtè", r.track.cues.single().text)
    }

    @Test fun a_broken_block_is_skipped_with_a_warning_and_the_rest_is_kept() {
        val r = ok("1\n00:00:01,000 --> 00:00:02,000\nGood\n\n2\nnot a time line\nBad\n\n3\n00:00:05,000 --> 00:00:04,000\nBackwards\n\n4\n00:00:06,000 --> 00:00:07,000\nLast\n")
        assertEquals(listOf("Good", "Last"), r.track.cues.map { it.text })
        assertEquals(2, r.warnings.size)
    }

    @Test fun empty_and_unusable_files_fail_cleanly() {
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Empty), parse("  \n \n"))
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Malformed), parse("this is not a subtitle file\nat all"))
    }

    @Test fun writing_numbers_the_cues_and_uses_comma_milliseconds() {
        val track = SubtitleTrack("t", "T", cues = listOf(SubtitleCue("a", 1_000, 2_500, "Hi\nthere"), SubtitleCue("b", 3_723_004, 3_724_000, "Bye")))
        assertEquals("1\n00:00:01,000 --> 00:00:02,500\nHi\nthere\n\n2\n01:02:03,004 --> 01:02:04,000\nBye\n\n", SubtitleCodecs.serialize(track, SubtitleFormat.SRT))
    }

    @Test fun the_track_offset_is_applied_when_writing() {
        val track = SubtitleTrack("t", "T", cues = listOf(SubtitleCue("a", 1_000, 2_000, "Hi")), offsetMs = 2_000)
        assertTrue(SubtitleCodecs.serialize(track, SubtitleFormat.SRT).contains("00:00:03,000 --> 00:00:04,000"))
    }

    @Test fun writing_then_reading_gives_the_same_cues() {
        val original = ok("1\n00:00:01,000 --> 00:00:02,500\n<b>Hello</b>\nworld\n\n2\n00:10:00,000 --> 00:10:01,000\nÜber\n")
        val again = ok(SubtitleCodecs.serialize(original.track, SubtitleFormat.SRT))
        assertEquals(original.track.cues.map { Triple(it.startMs, it.endMs, it.text) }, again.track.cues.map { Triple(it.startMs, it.endMs, it.text) })
    }
}
