package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayGeometryTest {
    private val t = TextOverlay("a", "Hi", 0, 1000, centerX = 0.25f, centerY = 0.8f, scale = 2f, widthFraction = 0.5f, rotationDeg = 30f, style = TextOverlayStyle(sizeFraction = 0.05f))

    @Test fun position_and_size_follow_the_frame() {
        val p = OverlayGeometry.toPixels(t, 1920, 1080)
        assertEquals(480f, p.centerX, 0.01f); assertEquals(864f, p.centerY, 0.01f)
        assertEquals(108f, p.fontSizePx, 0.01f); assertEquals(1920f, p.boxWidthPx!!, 0.01f); assertEquals(30f, p.rotationDeg)
    }
    @Test fun the_same_overlay_is_proportional_at_another_resolution() {
        val hd = OverlayGeometry.toPixels(t, 1920, 1080); val sd = OverlayGeometry.toPixels(t, 960, 540)
        assertEquals(hd.centerX / 2, sd.centerX, 0.01f); assertEquals(hd.centerY / 2, sd.centerY, 0.01f); assertEquals(hd.fontSizePx / 2, sd.fontSizePx, 0.01f)
    }
    @Test fun portrait_frames_scale_text_by_height() {
        assertEquals(0.05f * 1920, OverlayGeometry.toPixels(t.copy(scale = 1f), 1080, 1920).fontSizePx, 0.01f)
    }
    @Test fun no_width_means_no_wrapping() = assertNull(OverlayGeometry.toPixels(t.copy(widthFraction = null), 100, 100).boxWidthPx)
    @Test(expected = IllegalArgumentException::class) fun an_empty_frame_is_refused() { OverlayGeometry.toPixels(t, 0, 10) }
}
