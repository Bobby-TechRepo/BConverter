package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.fakes.SequentialIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssCodecTest {
    private val ass = """
        [Script Info]
        Title: Demo
        ScriptType: v4.00+
        PlayResX: 1280
        PlayResY: 720

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,1,2,10,10,30,1
        Style: Sign,Georgia,30,&H0000FFFF,&H000000FF,&H00FF0000,&H00000000,0,-1,1,0,100,100,0,0,3,0,0,8,10,10,20,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,Hello, world\NSecond line
        Dialogue: 0,0:01:00.10,0:01:01.00,Sign,,0,0,0,,{\an8\pos(100,50)}Top {\i1}sign{\i0} here
        Comment: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,not shown
    """.trimIndent()

    private fun parse(text: String, f: SubtitleFormat = SubtitleFormat.ASS) = SubtitleCodecs.parse(text, f, ids = SequentialIds()) as SubtitleParseResult.Parsed

    @Test fun script_info_styles_and_dialogue_are_read() {
        val t = parse(ass).track
        assertEquals("Demo", t.title); assertEquals(1280, t.playResX); assertEquals(720, t.playResY)
        assertEquals(2, t.cues.size)
        assertEquals("Hello, world\nSecond line", t.cues[0].text)   // commas inside the text are kept
        assertEquals(1_000L to 2_500L, t.cues[0].startMs to t.cues[0].endMs)
        assertEquals("Default", t.cues[0].styleName)
    }

    @Test fun style_definitions_keep_font_colours_border_and_alignment() {
        val d = parse(ass).track.styles.getValue("Default")
        assertEquals("Arial", d.fontFamily); assertEquals(48f, d.fontSize, 0f); assertTrue(d.bold); assertFalse(d.italic)
        assertEquals(0xFFFFFFFF.toInt(), d.primaryColor); assertEquals(0xFF000000.toInt(), d.outlineColor)
        assertEquals(0x7F000000, d.backColor)   // ASS alpha 80 (hex) = 128 transparent -> ARGB alpha 127
        assertEquals(2f, d.outlineWidth, 0f); assertEquals(1f, d.shadow, 0f); assertEquals(SubtitleAlignment.BOTTOM_CENTER, d.alignment)
        assertEquals(30, d.marginV)
        val s = parse(ass).track.styles.getValue("Sign")
        assertTrue(s.italic && s.underline && s.boxed); assertEquals(SubtitleAlignment.TOP_CENTER, s.alignment)
        assertEquals(0xFFFFFF00.toInt(), s.primaryColor)   // BGR 00FFFF -> yellow
    }

    @Test fun inline_italics_become_markup_and_other_leading_overrides_are_kept_aside() {
        val c = parse(ass).track.cues[1]
        assertEquals("Top <i>sign</i> here", c.text)
        assertEquals("""\an8\pos(100,50)""", c.assOverride)
        assertEquals(60_100L, c.startMs)
    }

    @Test fun comments_are_not_cues() { assertEquals(2, parse(ass).track.cues.size) }

    @Test fun writing_keeps_styles_overrides_and_times_so_it_round_trips() {
        val first = parse(ass).track
        val again = parse(SubtitleCodecs.serialize(first, SubtitleFormat.ASS)).track
        assertEquals(first.cues.map { listOf(it.startMs, it.endMs, it.text, it.styleName, it.assOverride) }, again.cues.map { listOf(it.startMs, it.endMs, it.text, it.styleName, it.assOverride) })
        assertEquals(first.styles, again.styles)
        assertEquals(1280, again.playResX)
    }

    @Test fun times_are_written_in_centiseconds() {
        val track = SubtitleTrack("t", "T", cues = listOf(SubtitleCue("a", 1_234, 5_678, "Hi")))
        val out = SubtitleCodecs.serialize(track, SubtitleFormat.ASS)
        assertTrue(out, out.contains("Dialogue: 0,0:00:01.23,0:00:05.68,Default,,0,0,0,,Hi"))
    }

    @Test fun a_track_without_styles_gets_a_default_style_when_written_as_ass() {
        val out = SubtitleCodecs.serialize(SubtitleTrack("t", "T", cues = listOf(SubtitleCue("a", 0, 1_000, "<i>x</i>\ny"))), SubtitleFormat.ASS)
        assertTrue(out.contains("[V4+ Styles]")); assertTrue(out.contains("Style: Default,"))
        assertTrue(out, out.contains("{\\i1}x{\\i0}\\Ny"))
    }

    @Test fun ssa_files_use_their_own_alignment_numbers_and_columns() {
        val ssa = """
            [Script Info]
            ScriptType: v4.00

            [V4 Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, TertiaryColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, AlphaLevel, Encoding
            Style: Default,Tahoma,20,16777215,255,0,0,0,0,1,2,1,6,10,10,15,0,1

            [Events]
            Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: Marked=0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Top text
        """.trimIndent()
        val t = parse(ssa, SubtitleFormat.SSA).track
        assertEquals(SubtitleAlignment.TOP_CENTER, t.styles.getValue("Default").alignment)   // SSA 6 = top centre
        assertEquals(0xFFFFFFFF.toInt(), t.styles.getValue("Default").primaryColor)
        assertEquals("Top text", t.cues.single().text)
        val out = SubtitleCodecs.serialize(t, SubtitleFormat.SSA)
        assertTrue(out.contains("[V4 Styles]")); assertTrue(out.contains(",6,10,10,15,"))
        assertEquals(SubtitleAlignment.TOP_CENTER, parse(out, SubtitleFormat.SSA).track.styles.getValue("Default").alignment)
    }

    @Test fun a_file_with_no_events_fails() {
        assertEquals(SubtitleParseResult.Failed(SubtitleError.Malformed), SubtitleCodecs.parse("[Script Info]\nTitle: x\n", SubtitleFormat.ASS, ids = SequentialIds()))
    }

    @Test fun inline_tags_that_cannot_be_kept_are_reported_not_hidden() {
        val r = parse("[Script Info]\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Red {\\c&H0000FF&}word")
        assertEquals("Red word", r.track.cues.single().text)
        assertTrue(r.warnings.isNotEmpty())
        assertNull(r.track.cues.single().assOverride)
    }
}
