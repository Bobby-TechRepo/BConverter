package io.bsoft.bconverter.domain.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTransformFiltersTest {
    private fun build(t: ClipTransform) = ClipTransformFilters.build(t, 1920, 1080)

    @Test fun nothing_to_do_means_no_filters() { assertEquals(emptyList<String>(), build(ClipTransform.None)) }
    @Test fun a_crop_is_in_pixels() {
        assertEquals(listOf("crop=960:540:480:270"), build(ClipTransform(crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f))))
    }
    @Test fun a_right_turn_is_transpose_1() { assertEquals(listOf("transpose=1"), build(ClipTransform(rotationDegrees = 90))) }
    @Test fun a_left_turn_is_transpose_2() { assertEquals(listOf("transpose=2"), build(ClipTransform(rotationDegrees = 270))) }
    @Test fun a_half_turn_is_both_flips() { assertEquals(listOf("hflip", "vflip"), build(ClipTransform(rotationDegrees = 180))) }
    @Test fun mirrors() {
        assertEquals(listOf("hflip"), build(ClipTransform(flipHorizontal = true)))
        assertEquals(listOf("vflip"), build(ClipTransform(flipVertical = true)))
    }
    @Test fun the_order_is_crop_then_turn_then_mirror() {
        val t = ClipTransform(CropRect(0f, 0f, 0.5f, 1f), 90, flipHorizontal = true, flipVertical = true)
        assertEquals(listOf("crop=960:1080:0:0", "transpose=1", "hflip", "vflip"), build(t))
    }
    @Test fun only_declared_filters_are_used() {
        val t = ClipTransform(CropRect(0.1f, 0.1f, 0.9f, 0.9f), 180, flipHorizontal = true, flipVertical = true)
        for (f in build(t) + build(ClipTransform(rotationDegrees = 90)) + build(ClipTransform(rotationDegrees = 270))) {
            assertTrue(f, f.substringBefore('=') in ClipTransformFilters.USED_FILTERS)
        }
    }
}
