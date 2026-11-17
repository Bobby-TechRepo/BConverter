package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayImageSizingTest {
    @Test fun a_small_picture_is_never_enlarged() { assertEquals(300 to 200, OverlayImageSizing.fit(300, 200, 2048)) }
    @Test fun a_large_picture_shrinks_to_the_longest_side_keeping_its_shape() {
        assertEquals(2048 to 1024, OverlayImageSizing.fit(8192, 4096, 2048))
        assertEquals(1024 to 2048, OverlayImageSizing.fit(4096, 8192, 2048))
    }
    @Test fun a_thin_picture_never_collapses_to_nothing() { assertEquals(2048 to 1, OverlayImageSizing.fit(100_000, 40, 2048)) }
    @Test fun the_decoder_sample_size_is_a_power_of_two_that_stays_at_least_the_target() {
        assertEquals(1, OverlayImageSizing.sampleSize(1000, 800, 2048))
        assertEquals(2, OverlayImageSizing.sampleSize(4096, 3000, 2048))
        assertEquals(4, OverlayImageSizing.sampleSize(8192, 6000, 2048))
        assertEquals(1, OverlayImageSizing.sampleSize(3000, 2000, 2048)) // halving would drop below the target
    }
    @Test fun absurd_sources_are_refused() {
        assertTrue(OverlayImageSizing.isAcceptable(4000, 3000))
        assertFalse(OverlayImageSizing.isAcceptable(20_000, 20_000))
        assertFalse(OverlayImageSizing.isAcceptable(0, 100))
    }
    @Test fun a_normal_picture_gets_the_standard_height() { assertEquals(0.3f, OverlayImageSizing.defaultHeightFraction(1f, 16f / 9f), 0f) }
    @Test fun a_very_wide_picture_is_kept_to_half_the_frame_width() {
        val f = OverlayImageSizing.defaultHeightFraction(6f, 16f / 9f) // 6:1 banner
        assertEquals(0.5f * (16f / 9f) / 6f, f, 0.0001f)
        assertTrue(f * 6f <= 0.5f * (16f / 9f) + 0.0001f)
    }
    @Test fun the_height_fraction_always_stays_within_the_models_range() {
        for (a in listOf(0.01f, 0.5f, 1f, 3f, 50f, 1000f)) {
            val f = OverlayImageSizing.defaultHeightFraction(a, 16f / 9f)
            assertTrue("aspect $a gave $f", f > 0f && f <= 0.3f)
            OverlayImage("/a.png", 10, 10, f)
        }
    }
}
