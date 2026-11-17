package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGestureSessionTest {
    private val o = TextOverlay("a", "Hi", 0, 1000, centerX = 0.3f, centerY = 0.3f)

    @Test fun dragging_moves_by_fractions_of_the_frame() {
        val r = OverlayGestureSession(o).update(0.1f, 0.2f, 1f, 0f).overlay
        assertEquals(0.4f, r.centerX, 0.0001f); assertEquals(0.5f, r.centerY, 0.0001f)
    }
    @Test fun movements_accumulate_across_updates() {
        val s = OverlayGestureSession(o); s.update(0.05f, 0f, 1f, 0f); val r = s.update(0.05f, 0f, 1f, 0f).overlay
        assertEquals(0.4f, r.centerX, 0.0001f)
    }
    @Test fun the_overlay_cannot_leave_the_frame() {
        val r = OverlayGestureSession(o).update(5f, -5f, 1f, 0f).overlay
        assertEquals(1f, r.centerX); assertEquals(0f, r.centerY)
    }
    @Test fun the_middle_is_a_snap_point_that_can_be_left_again() {
        val s = OverlayGestureSession(o.copy(centerX = 0.45f))
        val near = s.update(0.0f + 0.0f + 0.1f - 0.1f + 0.0f + 0.045f, 0f, 1f, 0f) // raw x = 0.495
        assertEquals(0.5f, near.overlay.centerX); assertTrue(near.snappedX)
        val away = s.update(0.03f, 0f, 1f, 0f) // raw x = 0.525, outside the tolerance
        assertEquals(0.525f, away.overlay.centerX, 0.0001f); assertFalse(away.snappedX)
    }
    @Test fun pinching_scales_within_limits() {
        val s = OverlayGestureSession(o)
        assertEquals(2f, s.update(0f, 0f, 2f, 0f).overlay.scale, 0.0001f)
        assertEquals(20f, s.update(0f, 0f, 100f, 0f).overlay.scale, 0.0001f)
        assertEquals(0.1f, OverlayGestureSession(o).update(0f, 0f, 0.001f, 0f).overlay.scale, 0.0001f)
    }
    @Test fun rotation_snaps_to_right_angles_and_is_normalised() {
        assertEquals(90f, OverlayGestureSession(o).update(0f, 0f, 1f, 88f).overlay.rotationDeg)
        assertEquals(30f, OverlayGestureSession(o).update(0f, 0f, 1f, 30f).overlay.rotationDeg)
        assertEquals(-90f, OverlayGestureSession(o).update(0f, 0f, 1f, 270f).overlay.rotationDeg)
        assertEquals(10f, OverlayGestureSession(o).update(0f, 0f, 1f, 370f).overlay.rotationDeg, 0.001f)
    }
    @Test fun bad_numbers_are_ignored() {
        val r = OverlayGestureSession(o).update(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NaN).overlay
        assertEquals(0.3f, r.centerX); assertEquals(0.3f, r.centerY); assertEquals(1f, r.scale); assertEquals(0f, r.rotationDeg)
    }
    @Test fun other_fields_are_untouched() = assertEquals(o.copy(centerX = 0.4f), OverlayGestureSession(o).update(0.1f, 0f, 1f, 0f).overlay)
}
