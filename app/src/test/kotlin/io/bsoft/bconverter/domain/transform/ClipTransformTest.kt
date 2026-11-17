package io.bsoft.bconverter.domain.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTransformTest {
    @Test fun none_is_the_identity() { assertTrue(ClipTransform.None.isIdentity); assertTrue(ClipTransform().isIdentity) }
    @Test fun any_change_is_not_the_identity() {
        assertFalse(ClipTransform(rotationDegrees = 90).isIdentity)
        assertFalse(ClipTransform(flipHorizontal = true).isIdentity)
        assertFalse(ClipTransform(flipVertical = true).isIdentity)
        assertFalse(ClipTransform(crop = CropRect(left = 0.2f)).isIdentity)
    }
    @Test(expected = IllegalArgumentException::class) fun only_quarter_turns_are_allowed() { ClipTransform(rotationDegrees = 45) }
    @Test fun rotating_right_four_times_comes_back() {
        var t = ClipTransform.None
        val seen = (1..4).map { t = t.rotatedRight(); t.rotationDegrees }
        assertEquals(listOf(90, 180, 270, 0), seen)
    }
    @Test fun rotating_left_goes_the_other_way() {
        assertEquals(270, ClipTransform.None.rotatedLeft().rotationDegrees)
        assertEquals(0, ClipTransform.None.rotatedLeft().rotatedRight().rotationDegrees)
    }
    @Test fun flips_toggle() {
        assertTrue(ClipTransform.None.flippedHorizontally().flipHorizontal)
        assertFalse(ClipTransform.None.flippedHorizontally().flippedHorizontally().flipHorizontal)
        assertTrue(ClipTransform.None.flippedVertically().flipVertical)
    }
    @Test fun a_quarter_turn_swaps_the_sides() {
        assertEquals(1080 to 1920, ClipTransform(rotationDegrees = 90).outputSize(1920, 1080))
        assertEquals(1080 to 1920, ClipTransform(rotationDegrees = 270).outputSize(1920, 1080))
        assertEquals(1920 to 1080, ClipTransform(rotationDegrees = 180).outputSize(1920, 1080))
    }
    @Test fun flips_do_not_change_the_size() {
        assertEquals(1920 to 1080, ClipTransform(flipHorizontal = true, flipVertical = true).outputSize(1920, 1080))
    }
    @Test fun a_crop_keeps_that_share_of_each_side() {
        assertEquals(960 to 540, ClipTransform(crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f)).outputSize(1920, 1080))
    }
    @Test fun cropped_sizes_are_even() {
        val (w, h) = ClipTransform(crop = CropRect(0f, 0f, 0.333f, 0.777f)).outputSize(1000, 1000)
        assertEquals(0, w % 2); assertEquals(0, h % 2)
    }
    @Test fun crop_then_rotate_swaps_the_cropped_sides() {
        assertEquals(540 to 960, ClipTransform(crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f), rotationDegrees = 90).outputSize(1920, 1080))
    }
    @Test fun the_crop_never_runs_past_the_picture() {
        for (len in listOf(2, 3, 99, 100, 1081, 1920)) for (l in listOf(0f, 0.1f, 0.333f, 0.5f)) for (r in listOf(0.7f, 0.99f, 1f)) {
            val kept = ClipTransform.croppedSide(len, l, r); val at = ClipTransform.cropOffset(len, l)
            assertTrue("len=$len l=$l r=$r kept=$kept at=$at", at >= 0 && kept >= 2 && (len < 2 || at + kept <= len))
        }
    }
}
