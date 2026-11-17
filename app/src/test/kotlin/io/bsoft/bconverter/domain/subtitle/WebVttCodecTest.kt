package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.fakes.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVttCodecTest {
    private fun ok(text: String) = SubtitleCodecs.parse(text, SubtitleFormat.WEBVTT, ids = SequentialIds()) as SubtitleParseResult.Parsed

    @Test fun cues_are_read_with_short_and_long_timestamps_and_identifiers() {
        val r = ok("WEBVTT\n\nintro\n00:01.000 --> 00:02.500 align:start position:10%\nHello\n\n01:00:00.000 --> 01:00:01.000\nLate\n")
        assertEquals(listOf(1_000L to 2_500L, 3_600_000L to 3_601_000L), r.track.cues.map { it.startMs to it.endMs })
        assertEquals(listOf("Hello", "Late"), r.track.cues.map { it.text })
    }

    @Test fun notes_style_blocks_and_the_header_description_are_not_cues() {
        val r = ok("WEBVTT - my file\n\nNOTE this is a comment\nover two lines\n\nSTYLE\n::cue { color: red }\n\n00:00.000 --> 00:01.000\nOnly cue\n")
        assertEquals(listOf("Only cue"), r.track.cues.map { it.text })
    }

    @Test fun entities_are_decoded_and_voice_and_class_tags_are_removed_but_basic_formatting_stays() {
        val r = ok("WEBVTT\n\n00:00.000 --> 00:01.000\n<v Bob>Tom &amp; <i>Jerry</i> &lt;3 <c.yellow>now</c>\n")
        assertEquals("Tom & <i>Jerry</i> <3 now", r.track.cues.single().text)
    }

    @Test fun a_file_without_the_header_is_rejected() {
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Malformed), SubtitleCodecs.parse("00:00.000 --> 00:01.000\nHi", SubtitleFormat.WEBVTT, ids = SequentialIds()))
    }

    @Test fun writing_uses_dot_milliseconds_and_escapes_markup_characters() {
        val track = SubtitleTrack("t", "T", cues = listOf(SubtitleCue("a", 1_000, 2_500, "Tom & <i>Jerry</i>")))
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.500\nTom &amp; <i>Jerry</i>\n\n", SubtitleCodecs.serialize(track, SubtitleFormat.WEBVTT))
    }

    @Test fun writing_then_reading_round_trips() {
        val original = ok("WEBVTT\n\n00:01.000 --> 00:02.500\nA &amp; B\nline two\n")
        val again = ok(SubtitleCodecs.serialize(original.track, SubtitleFormat.WEBVTT))
        assertEquals(original.track.cues.map { it.text }, again.track.cues.map { it.text })
        assertTrue(again.track.cues.single().startMs == 1_000L)
    }
}
