package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.fakes.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleDetectionTest {
    private fun detect(text: String, name: String? = null) = SubtitleCodecs.detect(text, name)

    @Test fun the_format_is_recognised_from_the_content() {
        assertEquals(SubtitleFormat.WEBVTT, detect("WEBVTT\n\n00:00.000 --> 00:01.000\nx"))
        assertEquals(SubtitleFormat.SRT, detect("1\n00:00:01,000 --> 00:00:02,000\nx"))
        assertEquals(SubtitleFormat.ASS, detect("[Script Info]\nScriptType: v4.00+\n[V4+ Styles]\n"))
        assertEquals(SubtitleFormat.SSA, detect("[Script Info]\nScriptType: v4.00\n[V4 Styles]\n"))
        assertEquals(SubtitleFormat.TTML, detect("<?xml version=\"1.0\"?><tt xmlns=\"http://www.w3.org/ns/ttml\"/>"))
        assertEquals(null, detect("just some text"))
    }

    @Test fun the_content_wins_over_a_misleading_file_name() {
        assertEquals(SubtitleFormat.SRT, detect("1\n00:00:01,000 --> 00:00:02,000\nx", "movie.vtt"))
    }

    @Test fun parsing_without_a_stated_format_detects_it() {
        val r = SubtitleCodecs.parse("WEBVTT\n\n00:00.000 --> 00:01.000\nHi", ids = SequentialIds()) as SubtitleParseResult.Parsed
        assertEquals(SubtitleFormat.WEBVTT, r.track.sourceFormat)
    }

    @Test fun an_unrecognised_file_is_unsupported_and_an_empty_one_is_empty() {
        assertEquals(SubtitleParseResult.Failed(SubtitleError.UnknownFormat), SubtitleCodecs.parse("hello there", ids = SequentialIds()))
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Empty), SubtitleCodecs.parse("   ", ids = SequentialIds()))
    }
}
