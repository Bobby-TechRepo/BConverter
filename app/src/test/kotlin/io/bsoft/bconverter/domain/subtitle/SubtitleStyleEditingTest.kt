package io.bsoft.bconverter.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleEditingTest {
    private val cue = SubtitleCue("c", 0, 1000, "Hi")
    private val plain = SubtitleTrack("t", "T", listOf(cue))
    private fun ok(r: SubtitleEdit) = (r as SubtitleEdit.Ok).track

    @Test fun a_track_without_styles_has_the_default_style() = assertEquals(SubtitleStyle(), SubtitleEditor.baseStyle(plain))

    @Test fun the_base_style_is_the_one_called_default_else_the_first() {
        val a = SubtitleStyle("Alt", fontSize = 20f); val d = SubtitleStyle("Default", fontSize = 30f)
        assertEquals(d, SubtitleEditor.baseStyle(plain.copy(styles = linkedMapOf("Alt" to a, "Default" to d))))
        assertEquals(a, SubtitleEditor.baseStyle(plain.copy(styles = linkedMapOf("Alt" to a))))
    }

    @Test fun updating_the_base_style_keeps_its_name_and_other_styles() {
        val other = SubtitleStyle("Alt")
        val t = ok(SubtitleEditor.updateBaseStyle(plain.copy(styles = mapOf("Default" to SubtitleStyle(), "Alt" to other)), SubtitleStyle(name = "ignored", bold = true, fontSize = 60f)))
        assertEquals(SubtitleStyle("Default", bold = true, fontSize = 60f), t.styles["Default"]); assertEquals(other, t.styles["Alt"])
    }

    @Test fun updating_a_track_without_styles_creates_the_default_one() =
        assertEquals(SubtitleStyle("Default", italic = true), ok(SubtitleEditor.updateBaseStyle(plain, SubtitleStyle(italic = true))).styles["Default"])

    @Test fun a_cue_can_use_a_named_style_that_exists() {
        val t = plain.copy(styles = mapOf("Default" to SubtitleStyle(), "Top" to SubtitleStyle("Top", alignment = SubtitleAlignment.TOP_CENTER)))
        assertEquals("Top", ok(SubtitleEditor.setCueStyle(t, "c", "Top")).cues.single().styleName)
        assertEquals(null, ok(SubtitleEditor.setCueStyle(ok(SubtitleEditor.setCueStyle(t, "c", "Top")), "c", null)).cues.single().styleName)
    }

    @Test fun an_unknown_style_or_cue_is_refused() {
        assertEquals(EditProblem.NoSuchStyle, (SubtitleEditor.setCueStyle(plain, "c", "Nope") as SubtitleEdit.Rejected).reason)
        assertEquals(EditProblem.NoSuchCue, (SubtitleEditor.setCueStyle(plain, "zz", null) as SubtitleEdit.Rejected).reason)
    }

    @Test fun presets_are_distinct_and_named() {
        val all = SubtitleStylePresets.all
        assertTrue(all.size >= 5); assertEquals(all.size, all.map { it.id }.toSet().size); assertEquals(all.size, all.map { it.style }.toSet().size)
    }

    @Test fun applying_a_preset_changes_the_look_but_keeps_the_style_name() {
        val t = ok(SubtitleEditor.applyPreset(plain, SubtitleStylePresets.BOXED))
        assertEquals("Default", t.styles.keys.single()); assertEquals(true, t.styles["Default"]!!.boxed)
        assertNotEquals(SubtitleStyle(), t.styles["Default"])
    }

    @Test fun every_preset_is_a_valid_ass_style_that_survives_a_round_trip() {
        SubtitleStylePresets.all.forEach { p ->
            val track = ok(SubtitleEditor.applyPreset(plain, p))
            val parsed = (SubtitleCodecs.parse(SubtitleCodecs.serialize(track, SubtitleFormat.ASS)) as SubtitleParseResult.Parsed).track
            assertEquals(p.id, track.styles["Default"], parsed.styles["Default"]!!.copy(fontFamily = null)) // no font chosen is written as Arial
        }
    }

    @Test fun styled_tracks_count_as_styled_for_export() {
        assertEquals(false, SubtitleExportPlanner.hasStyling(plain))
        assertEquals(true, SubtitleExportPlanner.hasStyling(ok(SubtitleEditor.applyPreset(plain, SubtitleStylePresets.BOXED))))
    }
}
