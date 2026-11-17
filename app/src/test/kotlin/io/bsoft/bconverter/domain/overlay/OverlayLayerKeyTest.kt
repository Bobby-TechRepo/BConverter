package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayerKeyTest {
    private val base = TextOverlay("a", "Hello", 0, 3000)
    private fun req(o: TextOverlay = base, w: Int = 1920, h: Int = 1080, font: String? = null, stamp: Long = 0) = OverlayRasterRequest(o, w, h, font, stamp)
    private fun key(r: OverlayRasterRequest) = OverlayLayerKey.of(r)

    @Test fun key_is_32_lowercase_hex_characters() = assertTrue(Regex("[0-9a-f]{32}").matches(key(req())))
    @Test fun same_request_same_key() = assertEquals(key(req()), key(req()))
    @Test fun id_time_position_and_animations_do_not_change_the_key() {
        val moved = base.copy(id = "b", startMs = 500, endMs = 9000, centerX = 0.1f, centerY = 0.9f, name = "x",
            animIn = OverlayAnimation(AnimationKind.FADE), animOut = OverlayAnimation(AnimationKind.SLIDE_UP))
        assertEquals(key(req()), key(req(moved)))
    }
    @Test fun text_changes_the_key() = assertNotEquals(key(req()), key(req(base.copy(text = "Hello!"))))
    @Test fun scale_rotation_and_width_change_the_key() {
        assertNotEquals(key(req()), key(req(base.copy(scale = 2f))))
        assertNotEquals(key(req()), key(req(base.copy(rotationDeg = 10f))))
        assertNotEquals(key(req()), key(req(base.copy(widthFraction = 0.5f))))
    }
    @Test fun style_changes_the_key() {
        assertNotEquals(key(req()), key(req(base.copy(style = base.style.copy(color = 0xFFFF0000.toInt())))))
        assertNotEquals(key(req()), key(req(base.copy(style = base.style.copy(outline = OverlayOutline(0xFF000000.toInt(), 0.05f))))))
        assertNotEquals(key(req()), key(req(base.copy(style = base.style.copy(fontWeight = 700)))))
    }
    @Test fun canvas_size_changes_the_key() {
        assertNotEquals(key(req()), key(req(w = 1280, h = 720)))
    }
    @Test fun font_file_and_stamp_change_the_key() {
        assertNotEquals(key(req()), key(req(font = "/f/a.ttf", stamp = 1)))
        assertNotEquals(key(req(font = "/f/a.ttf", stamp = 1)), key(req(font = "/f/a.ttf", stamp = 2)))
    }
    @Test fun canvas_must_be_positive() {
        try { req(w = 0); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { }
    }
}
