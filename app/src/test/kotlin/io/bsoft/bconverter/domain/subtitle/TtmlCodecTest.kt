package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.fakes.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlCodecTest {
    private val ttml = """<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml" xmlns:tts="http://www.w3.org/ns/ttml#styling" xml:lang="fr">
  <body><div>
    <p begin="00:00:01.000" end="00:00:02.500">Bonjour<br/>le monde</p>
    <p begin="5.5s" dur="2s"><span tts:fontStyle="italic">Salut</span> &amp; fin</p>
    <p begin="1500ms" end="2000ms">Court</p>
  </div></body>
</tt>"""

    private fun parse(text: String) = SubtitleCodecs.parse(text, SubtitleFormat.TTML, ids = SequentialIds())

    @Test fun clock_offset_and_duration_timings_are_understood() {
        val t = (parse(ttml) as SubtitleParseResult.Parsed).track
        assertEquals(listOf(1_000L to 2_500L, 1_500L to 2_000L, 5_500L to 7_500L), t.cues.map { it.startMs to it.endMs })
        assertEquals("fr", t.language)
    }

    @Test fun line_breaks_and_italic_spans_become_text_and_markup() {
        val t = (parse(ttml) as SubtitleParseResult.Parsed).track
        assertEquals("Bonjour\nle monde", t.cues.first { it.startMs == 1_000L }.text)
        assertEquals("<i>Salut</i> & fin", t.cues.first { it.startMs == 5_500L }.text)
    }

    @Test fun not_xml_or_not_ttml_fails() {
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Malformed), parse("this is not xml"))
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Malformed), parse("<html><body/></html>"))
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Empty), parse(""))
    }

    @Test fun writing_then_reading_round_trips() {
        val t = (parse(ttml) as SubtitleParseResult.Parsed).track
        val out = SubtitleCodecs.serialize(t, SubtitleFormat.TTML)
        assertTrue(out, out.contains("begin=\"00:00:01.000\""))
        val again = (parse(out) as SubtitleParseResult.Parsed).track
        assertEquals(t.cues.map { Triple(it.startMs, it.endMs, it.text) }, again.cues.map { Triple(it.startMs, it.endMs, it.text) })
        assertEquals("fr", again.language)
    }
}
