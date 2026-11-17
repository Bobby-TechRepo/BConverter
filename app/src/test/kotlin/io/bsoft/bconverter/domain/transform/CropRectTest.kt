package io.bsoft.bconverter.domain.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropRectTest {
    @Test fun the_default_is_the_whole_frame() {
        assertTrue(CropRect().isFull); assertTrue(CropRect.Full.isFull)
    }
    @Test fun a_partial_crop_is_not_full() {
        assertTrue(!CropRect(left = 0.1f).isFull)
    }
    @Test fun width_and_height_are_fractions() {
        val c = CropRect(0.1f, 0.2f, 0.6f, 0.9f)
        assertEquals(0.5f, c.width, 1e-6f); assertEquals(0.7f, c.height, 1e-6f)
    }
    @Test(expected = IllegalArgumentException::class) fun outside_the_frame_is_refused() { CropRect(right = 1.2f) }
    @Test(expected = IllegalArgumentException::class) fun negative_is_refused() { CropRect(left = -0.1f) }
    @Test(expected = IllegalArgumentException::class) fun too_thin_is_refused() { CropRect(left = 0.5f, right = 0.55f) }
    @Test(expected = IllegalArgumentException::class) fun nan_is_refused() { CropRect(top = Float.NaN) }
    @Test fun a_square_from_a_wide_frame_trims_the_sides() {
        val c = CropRect.centeredAspect(1f, 1920, 1080)
        assertEquals(0f, c.top, 0f); assertEquals(1f, c.bottom, 0f)
        assertEquals(1080f / 1920f, c.width, 1e-4f)
        assertEquals((1f - c.width) / 2f, c.left, 1e-4f)
    }
    @Test fun a_tall_crop_from_a_wide_frame_trims_the_sides_more() {
        val c = CropRect.centeredAspect(9f / 16f, 1920, 1080)
        assertEquals(0f, c.top, 0f)
        assertEquals((9f / 16f) / (16f / 9f), c.width, 1e-4f)
    }
    @Test fun a_wide_crop_from_a_square_frame_trims_top_and_bottom() {
        val c = CropRect.centeredAspect(2f, 1000, 1000)
        assertEquals(0f, c.left, 0f); assertEquals(1f, c.right, 0f)
        assertEquals(0.5f, c.height, 1e-4f); assertEquals(0.25f, c.top, 1e-4f)
    }
    @Test fun the_frames_own_shape_keeps_everything() {
        assertTrue(CropRect.centeredAspect(16f / 9f, 1920, 1080).let { it.width > 0.9999f && it.height > 0.9999f })
    }
}
