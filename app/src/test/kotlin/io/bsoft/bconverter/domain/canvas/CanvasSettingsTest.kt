package io.bsoft.bconverter.domain.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasSettingsTest {
    private fun size(aspect: CanvasAspect, w: Int, h: Int) = CanvasSettings(aspect = aspect).sizeFor(w, h)

    @Test fun the_default_changes_nothing() { assertTrue(CanvasSettings().isDefault); assertEquals(1920 to 1080, CanvasSettings().sizeFor(1920, 1080)) }
    @Test fun any_change_is_not_the_default() {
        assertFalse(CanvasSettings(fit = CanvasFit.FILL).isDefault)
        assertFalse(CanvasSettings(background = CanvasBackground.Blur).isDefault)
        assertFalse(CanvasSettings(background = CanvasBackground.Color(0xFFFFFF)).isDefault)
    }
    @Test fun auto_keeps_the_size_as_given() { assertEquals(1280 to 720, size(CanvasAspect.AUTO, 1280, 720)); assertEquals(1081 to 721, size(CanvasAspect.AUTO, 1081, 721)) }
    @Test fun wide_keeps_the_short_side() { assertEquals(1920 to 1080, size(CanvasAspect.WIDE, 1920, 1080)); assertEquals(1920 to 1080, size(CanvasAspect.WIDE, 1080, 1920)) }
    @Test fun tall_from_a_landscape_clip() { assertEquals(1080 to 1920, size(CanvasAspect.TALL, 1920, 1080)) }
    @Test fun square_uses_the_short_side() { assertEquals(1080 to 1080, size(CanvasAspect.SQUARE, 1920, 1080)); assertEquals(720 to 720, size(CanvasAspect.SQUARE, 720, 1280)) }
    @Test fun portrait_four_by_five() { assertEquals(1080 to 1350, size(CanvasAspect.PORTRAIT, 1920, 1080)) }
    @Test fun sides_are_even() {
        CanvasAspect.entries.filter { it != CanvasAspect.AUTO }.forEach { a ->
            val (w, h) = size(a, 853, 481)
            assertEquals("$a $w", 0, w % 2); assertEquals("$a $h", 0, h % 2)
        }
    }
    @Test fun tiny_pictures_do_not_collapse() { val (w, h) = size(CanvasAspect.TALL, 2, 2); assertTrue(w >= 2 && h >= 2) }
    @Test fun a_background_colour_must_be_a_colour() {
        CanvasBackground.Color(0); CanvasBackground.Color(0xFFFFFF)
        assertTrue(runCatching { CanvasBackground.Color(-1) }.isFailure); assertTrue(runCatching { CanvasBackground.Color(0x1000000) }.isFailure)
    }
}
