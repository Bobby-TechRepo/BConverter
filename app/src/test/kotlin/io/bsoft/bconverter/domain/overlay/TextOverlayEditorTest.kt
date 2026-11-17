package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayEditorTest {
    private val a = TextOverlay("a", "A", 1000, 3000)
    private val list = listOf(a)
    private fun ok(e: OverlayEdit) = (e as OverlayEdit.Ok).overlays
    private fun rejected(e: OverlayEdit) = (e as OverlayEdit.Rejected).reason

    @Test fun add_puts_the_new_overlay_on_top_and_rejects_duplicates() {
        assertEquals(listOf("a", "b"), ok(TextOverlayEditor.add(list, TextOverlay("b", "B", 0, 10))).map { it.id })
        assertEquals(OverlayProblem.DuplicateId, rejected(TextOverlayEditor.add(list, a)))
    }
    @Test fun remove_and_unknown_ids() {
        assertTrue(ok(TextOverlayEditor.remove(list, "a")).isEmpty())
        assertEquals(OverlayProblem.NoSuchOverlay, rejected(TextOverlayEditor.remove(list, "zz")))
        assertEquals(OverlayProblem.NoSuchOverlay, rejected(TextOverlayEditor.setText(list, "zz", "x")))
    }
    @Test fun text_and_name_change() {
        assertEquals("New", ok(TextOverlayEditor.setText(list, "a", "New")).single().text)
        assertEquals(null, ok(TextOverlayEditor.rename(list, "a", "  ")).single().name)
        assertEquals("Title", ok(TextOverlayEditor.rename(list, "a", "Title")).single().name)
    }
    @Test fun moving_is_kept_inside_the_frame() {
        val m = ok(TextOverlayEditor.moveTo(list, "a", 1.4f, -0.2f)).single()
        assertEquals(1f, m.centerX); assertEquals(0f, m.centerY)
        assertEquals(OverlayProblem.InvalidValue, rejected(TextOverlayEditor.moveTo(list, "a", Float.NaN, 0f)))
    }
    @Test fun nudge_moves_by_a_fraction_of_the_frame() {
        val m = ok(TextOverlayEditor.nudge(list, "a", 0.1f, -0.2f)).single()
        assertEquals(0.6f, m.centerX, 0.0001f); assertEquals(0.3f, m.centerY, 0.0001f)
    }
    @Test fun rotation_is_normalised() {
        fun r(d: Float) = ok(TextOverlayEditor.setRotation(list, "a", d)).single().rotationDeg
        assertEquals(10f, r(370f)); assertEquals(-90f, r(270f)); assertEquals(180f, r(180f)); assertEquals(180f, r(-180f)); assertEquals(-10f, r(-370f))
    }
    @Test fun pinch_scales_within_limits() {
        assertEquals(2f, ok(TextOverlayEditor.scaleBy(list, "a", 2f)).single().scale)
        assertEquals(20f, ok(TextOverlayEditor.scaleBy(list, "a", 1000f)).single().scale)
        assertEquals(0.1f, ok(TextOverlayEditor.scaleBy(list, "a", 0.0001f)).single().scale, 0.0001f)
        assertEquals(OverlayProblem.InvalidValue, rejected(TextOverlayEditor.scaleBy(list, "a", 0f)))
        assertEquals(OverlayProblem.InvalidValue, rejected(TextOverlayEditor.setScale(list, "a", -1f)))
    }
    @Test fun width_can_be_set_and_cleared() {
        val w = ok(TextOverlayEditor.setWidth(list, "a", 0.6f))
        assertEquals(0.6f, w.single().widthFraction); assertEquals(null, ok(TextOverlayEditor.setWidth(w, "a", null)).single().widthFraction)
        assertEquals(OverlayProblem.InvalidValue, rejected(TextOverlayEditor.setWidth(list, "a", 2f)))
    }
    @Test fun retime_validates() {
        val r = ok(TextOverlayEditor.retime(list, "a", 500, 900)).single()
        assertEquals(500L, r.startMs); assertEquals(900L, r.endMs)
        assertEquals(OverlayProblem.InvalidTiming, rejected(TextOverlayEditor.retime(list, "a", 900, 900)))
        assertEquals(OverlayProblem.InvalidTiming, rejected(TextOverlayEditor.retime(list, "a", -5, 900)))
    }
    @Test fun shift_keeps_the_length_and_stops_at_zero() {
        val s = ok(TextOverlayEditor.shift(list, "a", 500)).single(); assertEquals(1500L, s.startMs); assertEquals(3500L, s.endMs)
        val z = ok(TextOverlayEditor.shift(list, "a", -5000)).single(); assertEquals(0L, z.startMs); assertEquals(2000L, z.endMs)
    }
    @Test fun style_updates_are_checked() {
        assertEquals(700, ok(TextOverlayEditor.updateStyle(list, "a") { it.copy(fontWeight = 700) }).single().style.fontWeight)
        assertEquals(OverlayProblem.InvalidValue, rejected(TextOverlayEditor.updateStyle(list, "a") { it.copy(opacity = 3f) }))
        assertEquals(OverlayProblem.InvalidValue, rejected(TextOverlayEditor.updateStyle(list, "a") { it.copy(outline = OverlayOutline(widthFraction = 9f)) }))
    }
    @Test fun animations_are_set_and_cleared() {
        val anim = OverlayAnimation(AnimationKind.FADE)
        val s = ok(TextOverlayEditor.setAnimations(list, "a", anim, null)).single()
        assertEquals(anim, s.animIn); assertEquals(null, s.animOut)
    }
    @Test fun duplicate_goes_on_top_after_the_original() {
        val d = ok(TextOverlayEditor.duplicate(list, "a", "b"))
        assertEquals(listOf("a", "b"), d.map { it.id }); assertEquals(3000L, d[1].startMs); assertEquals(5000L, d[1].endMs)
        assertEquals(OverlayProblem.DuplicateId, rejected(TextOverlayEditor.duplicate(list, "a", "a")))
    }
    @Test fun visible_overlays_are_listed_back_to_front() {
        val l = listOf(a, TextOverlay("b", "B", 0, 1500), TextOverlay("c", "C", 5000, 6000))
        assertEquals(listOf("a", "b"), TextOverlayEditor.visibleAt(l, 1200).map { it.id })
        assertTrue(TextOverlayEditor.visibleAt(l, 4000).isEmpty())
    }
    @Test fun the_input_list_is_never_changed() { TextOverlayEditor.setText(list, "a", "Z"); assertEquals("A", list.single().text) }
}
