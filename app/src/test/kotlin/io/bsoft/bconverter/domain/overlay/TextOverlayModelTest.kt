package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TextOverlayModelTest {
    private fun bad(f: () -> Unit) = try { f(); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
    private fun o(start: Long = 0, end: Long = 1000) = TextOverlay("a", "Hi", start, end)

    @Test fun defaults_are_a_centred_white_caption() {
        val t = o()
        assertEquals(0.5f, t.centerX); assertEquals(0.5f, t.centerY); assertEquals(1f, t.scale); assertEquals(0f, t.rotationDeg)
        assertEquals(1000L, t.durationMs)
    }
    @Test fun an_overlay_is_shown_from_start_up_to_but_not_including_end() {
        val t = o(100, 200)
        assertFalse(t.isShownAt(99)); assertTrue(t.isShownAt(100)); assertTrue(t.isShownAt(199)); assertFalse(t.isShownAt(200))
    }
    @Test fun bad_timing_is_refused() { bad { o(-1, 5) }; bad { o(5, 5) }; bad { o(6, 5) } }
    @Test fun blank_id_and_non_finite_values_are_refused() {
        bad { TextOverlay(" ", "x", 0, 1) }; bad { TextOverlay("a", "x", 0, 1, centerX = Float.NaN) }
        bad { TextOverlay("a", "x", 0, 1, scale = 0f) }; bad { TextOverlay("a", "x", 0, 1, widthFraction = 1.5f) }
    }
    @Test fun style_ranges_are_enforced() {
        bad { TextOverlayStyle(fontWeight = 50) }; bad { TextOverlayStyle(sizeFraction = 0f) }; bad { TextOverlayStyle(opacity = 2f) }
        bad { TextOverlayStyle(lineSpacing = 0f) }; bad { TextOverlayStyle(letterSpacing = 9f) }
        bad { OverlayOutline(widthFraction = -1f) }; bad { OverlayGlow(radius = 5f) }; bad { OverlayBackground(padding = -1f) }
        bad { OverlayShadow(blur = -1f) }; bad { OverlayAnimation(AnimationKind.FADE, 0) }
    }
    @Test fun effects_can_be_combined_on_one_overlay() {
        val s = TextOverlayStyle(outline = OverlayOutline(), shadow = OverlayShadow(), glow = OverlayGlow(), background = OverlayBackground(), letterSpacing = 0.1f, lineSpacing = 1.4f)
        assertTrue(s.outline != null && s.shadow != null && s.glow != null && s.background != null)
    }
    @Test fun animation_kinds_are_open_ended() {
        assertEquals(6, AnimationKind.builtIn.size)
        assertEquals(AnimationKind("fade"), AnimationKind.FADE)
        OverlayAnimation(AnimationKind("future_wobble"), 200) // unknown kinds are allowed
    }
}
