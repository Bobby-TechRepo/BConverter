package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayImageInteractionTest {
    private val img = OverlayImage("/a.png", 400, 200) // 2:1, 0.3 of the height
    private val pic = TextOverlay("p", "", 0, 5000, image = img)

    @Test fun a_pictures_box_is_its_exact_pixel_size() {
        val b = OverlayBounds.estimate(pic, 1920, 1080)
        assertEquals(648f, b.widthPx, 0.01f); assertEquals(324f, b.heightPx, 0.01f)
    }
    @Test fun a_touch_inside_the_picture_hits_and_one_far_outside_misses() {
        assertTrue(OverlayHitTest.hits(pic, 960f + 300f, 540f, 1920, 1080))
        assertFalse(OverlayHitTest.hits(pic, 960f + 400f, 540f, 1920, 1080))
    }
    @Test fun rotation_turns_the_hit_area() {
        val turned = pic.copy(rotationDeg = 90f) // now 324 wide, 648 tall
        assertTrue(OverlayHitTest.hits(turned, 960f, 540f + 300f, 1920, 1080))
        assertFalse(OverlayHitTest.hits(turned, 960f + 300f, 540f, 1920, 1080))
    }
    @Test fun a_picture_above_a_caption_is_hit_first_and_below_it_second() {
        val text = TextOverlay("t", "Hello", 0, 5000)
        assertEquals("p", OverlayHitTest.topmostAt(listOf(text, pic), 100, 960f, 540f, 1920, 1080))
        assertEquals("t", OverlayHitTest.topmostAt(listOf(pic, text), 100, 960f, 540f, 1920, 1080))
    }
    @Test fun a_new_picture_starts_at_the_playhead_in_the_middle() {
        val o = TextOverlayDefaults.newImageAt("i", 2_000, img)
        assertEquals(img, o.image); assertEquals(2_000L, o.startMs); assertEquals(5_000L, o.endMs)
        assertEquals(0.5f, o.centerX, 0f); assertEquals(0.5f, o.centerY, 0f); assertEquals("", o.text)
        assertNull(o.style.outline)
    }
    @Test fun a_picture_can_be_layered_with_text_in_one_order() {
        val list = listOf(pic, TextOverlay("t", "Hi", 0, 1000))
        val front = (TextOverlayEditor.bringToFront(list, "p") as OverlayEdit.Ok).overlays
        assertEquals(listOf("t", "p"), front.map { it.id })
    }
}
