package io.bsoft.bconverter.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipSoundTest {
    @Test fun the_default_is_full_volume_and_no_fades() { assertTrue(ClipSound().isDefault); assertTrue(ClipSound.Default.isDefault) }
    @Test fun any_change_is_not_the_default() {
        assertFalse(ClipSound(volume = 0.5f).isDefault); assertFalse(ClipSound(fadeInMs = 1).isDefault); assertFalse(ClipSound(fadeOutMs = 1).isDefault)
    }
    @Test fun the_limits_are_allowed() { ClipSound(0f, 0, 0); ClipSound(2f, 10_000, 10_000) }
    @Test(expected = IllegalArgumentException::class) fun volume_above_the_limit_is_refused() { ClipSound(volume = 2.1f) }
    @Test(expected = IllegalArgumentException::class) fun negative_volume_is_refused() { ClipSound(volume = -0.1f) }
    @Test(expected = IllegalArgumentException::class) fun nan_volume_is_refused() { ClipSound(volume = Float.NaN) }
    @Test(expected = IllegalArgumentException::class) fun a_negative_fade_is_refused() { ClipSound(fadeInMs = -1) }
    @Test(expected = IllegalArgumentException::class) fun a_fade_past_the_limit_is_refused() { ClipSound(fadeOutMs = 10_001) }
    @Test fun fades_that_fit_are_kept() { assertEquals(1_000L to 2_000L, ClipSound.fit(1_000, 2_000, 10_000)) }
    @Test fun a_fade_longer_than_the_clip_is_cut_to_it() { assertEquals(3_000L to 0L, ClipSound.fit(5_000, 1_000, 3_000)) }
    @Test fun the_fade_in_wins_when_both_do_not_fit() { assertEquals(2_000L to 1_000L, ClipSound.fit(2_000, 5_000, 3_000)) }
    @Test fun an_empty_clip_has_no_fades() { assertEquals(0L to 0L, ClipSound.fit(1_000, 1_000, 0)) }
}
