package io.bsoft.bconverter.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversePolicyTest {
    @Test fun smaller_pictures_allow_longer_clips() {
        assertTrue(ReversePolicy.maxDurationMs(1280, 720, 30.0) > ReversePolicy.maxDurationMs(1920, 1080, 30.0))
    }
    @Test fun a_higher_frame_rate_allows_less() {
        assertTrue(ReversePolicy.maxDurationMs(1920, 1080, 60.0) < ReversePolicy.maxDurationMs(1920, 1080, 30.0))
    }
    @Test fun full_hd_at_30_fps_fits_a_couple_of_seconds() {
        assertEquals(2_900.0, ReversePolicy.maxDurationMs(1920, 1080, 30.0).toDouble(), 100.0)
    }
    @Test fun there_is_always_at_least_a_second() {
        assertEquals(1_000L, ReversePolicy.maxDurationMs(7680, 4320, 120.0))
    }
    @Test fun nonsense_sizes_give_the_minimum() {
        assertEquals(1_000L, ReversePolicy.maxDurationMs(0, 0, 30.0))
    }
}
