package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayImageTest {
    private val img = OverlayImage("/data/pics/logo.png", 400, 200)
    private val o = TextOverlay("a", "", 0, 3000, image = img)

    @Test fun a_text_overlay_has_no_image() { assertFalse(TextOverlay("t", "Hi", 0, 1000).isImage) }
    @Test fun an_overlay_with_an_image_is_a_picture() { assertTrue(o.isImage) }
    @Test fun an_image_overlay_may_have_empty_text() { assertEquals("", o.text) }

    @Test(expected = IllegalArgumentException::class) fun a_blank_path_is_rejected() { OverlayImage(" ", 10, 10) }
    @Test(expected = IllegalArgumentException::class) fun a_zero_size_is_rejected() { OverlayImage("/a.png", 0, 10) }
    @Test(expected = IllegalArgumentException::class) fun a_height_fraction_over_one_is_rejected() { OverlayImage("/a.png", 10, 10, heightFraction = 1.5f) }
    @Test(expected = IllegalArgumentException::class) fun a_zero_height_fraction_is_rejected() { OverlayImage("/a.png", 10, 10, heightFraction = 0f) }

    @Test fun the_default_size_is_a_third_of_the_frame_height() { assertEquals(0.3f, img.heightFraction, 0f) }
    @Test fun aspect_ratio_is_width_over_height() { assertEquals(2f, img.aspect, 0f) }

    @Test fun pixel_size_follows_the_frame_height_and_keeps_the_aspect_ratio() {
        val s = OverlayGeometry.imageSizePx(o, 1920, 1080)
        assertEquals(324f, s.second, 0.01f); assertEquals(648f, s.first, 0.01f)
    }
    @Test fun scale_enlarges_both_sides() {
        val s = OverlayGeometry.imageSizePx(o.copy(scale = 2f), 1920, 1080)
        assertEquals(648f, s.second, 0.01f); assertEquals(1296f, s.first, 0.01f)
    }
    @Test fun the_same_overlay_is_proportional_at_another_resolution() {
        val a = OverlayGeometry.imageSizePx(o, 1920, 1080); val b = OverlayGeometry.imageSizePx(o, 960, 540)
        assertEquals(a.first / 2, b.first, 0.01f)
    }
    @Test(expected = IllegalArgumentException::class) fun pixel_size_of_a_text_overlay_is_an_error() {
        OverlayGeometry.imageSizePx(TextOverlay("t", "Hi", 0, 1000), 1920, 1080)
    }
}
