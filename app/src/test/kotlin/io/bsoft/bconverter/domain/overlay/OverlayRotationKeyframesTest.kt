package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class OverlayRotationKeyframesTest {
    private fun kf(t: Long, r: Float, eased: Boolean = false) = OverlayKeyframe(t, 0.5f, 0.5f, 1f, 1f, eased, r)
    private val a = TextOverlay("a", "A", 1_000, 3_000, centerX = 0.3f, centerY = 0.6f, rotationDeg = 10f)

    @Test fun a_keyframe_turns_nothing_unless_told_to() { assertEquals(0f, OverlayKeyframe(0, 0.5f, 0.5f).rotationDeg, 0f) }

    @Test fun the_turn_is_limited_to_two_full_circles_either_way() {
        OverlayKeyframe(0, 0.5f, 0.5f, rotationDeg = 720f); OverlayKeyframe(0, 0.5f, 0.5f, rotationDeg = -720f)
        assertThrows(IllegalArgumentException::class.java) { OverlayKeyframe(0, 0.5f, 0.5f, rotationDeg = 721f) }
        assertThrows(IllegalArgumentException::class.java) { OverlayKeyframe(0, 0.5f, 0.5f, rotationDeg = Float.NaN) }
    }

    @Test fun the_turn_changes_in_a_straight_line_between_keyframes() {
        assertEquals(45f, OverlayKeyframes.interpolate(listOf(kf(0, 0f), kf(2_000, 90f)), 1_000).rotationDeg, 0.001f)
    }

    @Test fun an_eased_step_turns_slowly_at_both_ends() {
        val k = listOf(kf(0, 0f, eased = true), kf(1_000, 100f))
        assertEquals(50f, OverlayKeyframes.interpolate(k, 500).rotationDeg, 0.001f)
        assertEquals(true, OverlayKeyframes.interpolate(k, 100).rotationDeg < 10f)
    }

    @Test fun the_turn_holds_before_the_first_and_after_the_last_keyframe() {
        val k = listOf(kf(500, 30f), kf(1_500, 90f))
        assertEquals(30f, OverlayKeyframes.interpolate(k, 0).rotationDeg, 0f); assertEquals(90f, OverlayKeyframes.interpolate(k, 9_000).rotationDeg, 0f)
    }

    @Test fun a_new_keyframe_starts_with_the_turn_it_would_have_there() {
        val withKeys = a.copy(keyframes = listOf(kf(0, 0f), kf(2_000, 80f)))
        assertEquals(40f, OverlayKeyframes.addAt(withKeys, 1_000)[1].rotationDeg, 0.001f)
    }

    @Test fun the_first_keyframe_added_does_not_turn_the_overlay() { assertEquals(0f, OverlayKeyframes.addAt(a, 0).single().rotationDeg, 0f) }

    @Test fun setting_a_look_stores_the_turn_and_clamps_it() {
        val k = OverlayKeyframes.withLookAt(a, 500, OverlayLook(0.5f, 0.5f, 1f, 1f, rotationDeg = 90f)).single()
        assertEquals(90f, k.rotationDeg, 0f)
        assertEquals(720f, OverlayKeyframes.withLookAt(a, 500, OverlayLook(0.5f, 0.5f, 1f, 1f, rotationDeg = 5_000f)).single().rotationDeg, 0f)
    }

    @Test fun editing_a_keyframe_keeps_its_easing_and_time() {
        val c = a.copy(keyframes = listOf(kf(0, 0f, eased = true), kf(1_500, 10f)))
        val edited = OverlayKeyframes.withLookAt(c, 30, OverlayLook(0.5f, 0.5f, 1f, 1f, rotationDeg = 60f))
        assertEquals(OverlayKeyframe(0, 0.5f, 0.5f, 1f, 1f, true, 60f), edited[0])
    }

    @Test fun the_overlay_at_a_moment_is_turned_by_its_own_rotation_plus_the_keyframe_turn() {
        val c = a.copy(keyframes = listOf(kf(0, 0f), kf(2_000, 90f)))
        assertEquals(10f + 45f, OverlayKeyframes.positioned(c, 2_000).rotationDeg, 0.001f)
    }

    @Test fun the_preview_frame_carries_the_turn() {
        val c = a.copy(keyframes = listOf(kf(0, 0f), kf(2_000, 90f)))
        assertEquals(45f, OverlayAnimator.frameAt(c, 2_000)!!.rotationDeg, 0.001f)
        assertEquals(0f, OverlayAnimator.frameAt(a, 2_000)!!.rotationDeg, 0f)
    }

    @Test fun the_still_frame_does_not_turn() { assertFalse(OverlayFrame.Still.rotationDeg != 0f) }
}
