package io.bsoft.bconverter.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPreviewTest {
    private fun look(f: ClipFilter) = FilterPreview.of(f)!!

    @Test fun the_identity_has_no_preview_look() { assertNull(FilterPreview.of(ClipFilter.None)) }
    @Test fun effects_the_preview_cannot_show_alone_leave_it_untouched() {
        assertNull(FilterPreview.of(ClipFilter(blur = 1f, vignette = 1f, grain = 1f, sharpen = 1f)))
        assertTrue(FilterPreview.hidesEffects(ClipFilter(blur = 1f)))
        assertTrue(!FilterPreview.hidesEffects(ClipFilter(contrast = 0.3f)))
    }
    @Test fun saturation_maps_to_percent() { assertEquals(-50f, look(ClipFilter(saturation = -0.5f)).saturation, 0.01f) }
    @Test fun contrast_matches_the_export_scale() { assertEquals(0.4f, look(ClipFilter(contrast = 0.5f)).contrast, 0.001f) }
    @Test fun warm_raises_red_and_lowers_blue() {
        val l = look(ClipFilter(temperature = 1f)); assertTrue(l.red > 1f && l.blue < 1f)
    }
    @Test fun brighter_exposure_lifts_lightness() { assertTrue(look(ClipFilter(exposure = 1f)).lightness > 0f) }
    @Test fun hue_is_degrees() { assertEquals(90f, look(ClipFilter(hue = 0.5f)).hueDegrees, 0.01f) }
    @Test fun black_and_white_removes_all_saturation() { assertEquals(-100f, look(ClipFilter(tone = ToneMode.BLACK_WHITE)).saturation, 0f) }
    @Test fun sepia_is_grey_tinted_warm() {
        val l = look(ClipFilter(tone = ToneMode.SEPIA)); assertEquals(-100f, l.saturation, 0f); assertTrue(l.red > l.blue)
    }
    @Test fun every_value_stays_in_the_ranges_the_player_accepts() {
        for (p in FilterPresets.all + FilterPreset("max", ClipFilter(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, ToneMode.SEPIA)) +
            FilterPreset("min", ClipFilter(-1f, -1f, -1f, -1f, -1f, -1f, -1f, 0f, 0f, 0f, 0f, -1f, -1f, -1f, 0f))) {
            val l = FilterPreview.of(p.filter) ?: continue
            assertTrue(p.id, l.saturation in -100f..100f && l.lightness in -100f..100f && l.contrast in -1f..1f)
            assertTrue(p.id, l.red >= 0f && l.green >= 0f && l.blue >= 0f)
        }
    }
}
