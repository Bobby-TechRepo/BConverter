package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHitTestTest {
    private val o = TextOverlay("a", "Hello", 0, 1000, style = TextOverlayStyle(sizeFraction = 0.1f)) // 108 px font on 1080 high
    private val w = 1920; private val h = 1080

    @Test fun a_keyframed_overlay_is_hit_where_it_is_not_where_it_started() {
        val moving = o.copy(keyframes = listOf(OverlayKeyframe(0, 0.1f, 0.5f), OverlayKeyframe(1_000, 0.9f, 0.5f)))
        assertEquals("a", OverlayHitTest.topmostAt(listOf(moving), 900, 0.82f * w, 540f, w, h))   // 90 % of the way along
        assertNull(OverlayHitTest.topmostAt(listOf(moving), 900, 0.1f * w, 540f, w, h))           // where it began; it has moved on
    }
    @Test fun bounds_grow_with_text_and_lines() {
        val one = OverlayBounds.estimate(o, w, h); val two = OverlayBounds.estimate(o.copy(text = "Hello\nWorld!!"), w, h)
        assertTrue(two.heightPx > one.heightPx * 1.9f); assertTrue(two.widthPx > one.widthPx)
    }
    @Test fun bounds_scale_with_the_frame_and_the_overlay_scale() {
        val a = OverlayBounds.estimate(o, 1920, 1080); val b = OverlayBounds.estimate(o, 960, 540); val c = OverlayBounds.estimate(o.copy(scale = 2f), 1920, 1080)
        assertEquals(a.widthPx / 2, b.widthPx, 0.5f); assertEquals(a.widthPx * 2, c.widthPx, 0.5f)
    }
    @Test fun a_wrap_width_limits_the_box_width_and_adds_height() {
        val long = o.copy(text = "A fairly long caption that has to wrap", widthFraction = 0.3f)
        val box = OverlayBounds.estimate(long, w, h)
        assertTrue(box.widthPx <= 0.3f * w + 0.5f); assertTrue(box.heightPx > OverlayBounds.estimate(long.copy(widthFraction = null), w, h).heightPx)
    }
    @Test fun a_touch_in_the_middle_hits_and_one_far_away_does_not() {
        assertTrue(OverlayHitTest.hits(o, 960f, 540f, w, h)); assertFalse(OverlayHitTest.hits(o, 100f, 100f, w, h))
    }
    @Test fun the_hit_follows_the_rotation() {
        val long = o.copy(text = "WIDE TEXT HERE", rotationDeg = 90f)
        val box = OverlayBounds.estimate(long, w, h)
        assertTrue("rotated: reaches up", OverlayHitTest.hits(long, 960f, 540f - box.widthPx / 2 + 10, w, h))
        assertFalse("rotated: no longer reaches sideways", OverlayHitTest.hits(long, 960f + box.widthPx / 2 - 10, 540f, w, h))
    }
    @Test fun the_topmost_overlay_wins_and_hidden_ones_are_ignored() {
        val below = o.copy(id = "below"); val above = o.copy(id = "above"); val later = o.copy(id = "later", startMs = 5000, endMs = 6000)
        assertEquals("above", OverlayHitTest.topmostAt(listOf(below, above), 500, 960f, 540f, w, h))
        assertEquals("below", OverlayHitTest.topmostAt(listOf(below, later), 500, 960f, 540f, w, h))
        assertNull(OverlayHitTest.topmostAt(listOf(later), 500, 960f, 540f, w, h))
        assertNull(OverlayHitTest.topmostAt(emptyList(), 0, 0f, 0f, w, h))
    }
}
