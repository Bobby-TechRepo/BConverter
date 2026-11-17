package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.fakes.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleConversionTest {
    private val styled = SubtitleTrack(
        "t", "T", styles = mapOf("Default" to SubtitleStyle(name = "Default", fontFamily = "Arial", bold = true)),
        cues = listOf(SubtitleCue("a", 0, 1_000, "x", styleName = "Default", assOverride = """\an8""")),
    )

    @Test fun converting_a_styled_track_to_a_plain_format_reports_what_is_lost() {
        assertEquals(setOf(StyleFeature.NAMED_STYLES, StyleFeature.POSITIONING), SubtitleCodecs.lostInConversion(styled, SubtitleFormat.SRT))
        assertEquals(setOf(StyleFeature.NAMED_STYLES, StyleFeature.POSITIONING), SubtitleCodecs.lostInConversion(styled, SubtitleFormat.WEBVTT))
    }

    @Test fun nothing_is_lost_between_ass_and_ssa_or_for_plain_tracks() {
        assertEquals(emptySet<StyleFeature>(), SubtitleCodecs.lostInConversion(styled, SubtitleFormat.ASS))
        assertEquals(emptySet<StyleFeature>(), SubtitleCodecs.lostInConversion(styled.copy(styles = emptyMap(), cues = listOf(SubtitleCue("a", 0, 1_000, "x"))), SubtitleFormat.SRT))
    }

    @Test fun converting_between_formats_keeps_text_and_timing() {
        val srt = "1\n00:00:01,000 --> 00:00:02,500\n<i>Hi</i>\nthere\n"
        val track = (SubtitleCodecs.parse(srt, ids = SequentialIds()) as SubtitleParseResult.Parsed).track
        for (target in SubtitleFormat.entries) {
            val back = (SubtitleCodecs.parse(SubtitleCodecs.serialize(track, target), target, ids = SequentialIds()) as SubtitleParseResult.Parsed).track
            val c = back.cues.single()
            assertEquals("$target", Triple(1_000L, 2_500L, "<i>Hi</i>\nthere"), Triple(c.startMs, c.endMs, c.text))
        }
    }

    @Test fun ass_time_rounding_is_to_the_nearest_centisecond() {
        val t = SubtitleTrack("t", "T", cues = listOf(SubtitleCue("a", 1_005, 2_994, "x")))
        assertTrue(SubtitleCodecs.serialize(t, SubtitleFormat.ASS).contains("0:00:01.01,0:00:02.99"))
    }
}
