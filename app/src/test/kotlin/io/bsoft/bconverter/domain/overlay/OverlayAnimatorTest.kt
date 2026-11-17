package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayAnimatorTest {
    private fun o(inA: OverlayAnimation? = null, outA: OverlayAnimation? = null, end: Long = 2000) =
        TextOverlay("a", "Hi", 1000, 1000 + end, animIn = inA, animOut = outA)
    private fun lin(k: AnimationKind, ms: Long = 500) = OverlayAnimation(k, ms, OverlayEasing.LINEAR)

    @Test fun not_shown_outside_its_time() {
        assertNull(OverlayAnimator.frameAt(o(), 999)); assertNull(OverlayAnimator.frameAt(o(), 3000))
    }
    @Test fun without_animations_it_is_always_fully_visible() = assertEquals(OverlayFrame.Still, OverlayAnimator.frameAt(o(), 1500))
    @Test fun fade_in_ramps_alpha_from_zero_to_one() {
        val t = o(inA = lin(AnimationKind.FADE))
        assertEquals(0f, OverlayAnimator.frameAt(t, 1000)!!.alpha, 0.001f)
        assertEquals(0.5f, OverlayAnimator.frameAt(t, 1250)!!.alpha, 0.001f)
        assertEquals(1f, OverlayAnimator.frameAt(t, 1500)!!.alpha, 0.001f)
    }
    @Test fun fade_out_ramps_alpha_to_zero_at_the_end() {
        val t = o(outA = lin(AnimationKind.FADE))
        assertEquals(1f, OverlayAnimator.frameAt(t, 2500)!!.alpha, 0.001f)
        assertEquals(0.5f, OverlayAnimator.frameAt(t, 2750)!!.alpha, 0.001f)
        assertEquals(0.002f, OverlayAnimator.frameAt(t, 2999)!!.alpha, 0.01f)
    }
    @Test fun slides_start_offset_and_arrive_at_zero() {
        assertEquals(-OverlayAnimator.SLIDE_DISTANCE, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SLIDE_LEFT)), 1000)!!.offsetX, 0.001f)
        assertEquals(OverlayAnimator.SLIDE_DISTANCE, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SLIDE_RIGHT)), 1000)!!.offsetX, 0.001f)
        assertEquals(-OverlayAnimator.SLIDE_DISTANCE, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SLIDE_UP)), 1000)!!.offsetY, 0.001f)
        assertEquals(OverlayAnimator.SLIDE_DISTANCE, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SLIDE_DOWN)), 1000)!!.offsetY, 0.001f)
        assertEquals(0f, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SLIDE_LEFT)), 1500)!!.offsetX, 0.001f)
    }
    @Test fun slide_out_leaves_in_the_named_direction() =
        assertEquals(-OverlayAnimator.SLIDE_DISTANCE * 0.5f, OverlayAnimator.frameAt(o(outA = lin(AnimationKind.SLIDE_LEFT)), 2750)!!.offsetX, 0.001f)
    @Test fun scale_in_grows_to_full_size() {
        assertEquals(OverlayAnimator.SCALE_FROM, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SCALE)), 1000)!!.scale, 0.001f)
        assertEquals(1f, OverlayAnimator.frameAt(o(inA = lin(AnimationKind.SCALE)), 1600)!!.scale, 0.001f)
    }
    @Test fun in_and_out_together_leave_the_middle_untouched() {
        val t = o(lin(AnimationKind.FADE), lin(AnimationKind.FADE))
        assertEquals(OverlayFrame.Still, OverlayAnimator.frameAt(t, 2000))
    }
    @Test fun animations_longer_than_the_overlay_shrink_instead_of_overlapping() {
        val t = TextOverlay("a", "x", 0, 400, animIn = lin(AnimationKind.FADE, 1000), animOut = lin(AnimationKind.FADE, 1000))
        assertEquals(0f, OverlayAnimator.frameAt(t, 0)!!.alpha, 0.001f)
        assertEquals(1f, OverlayAnimator.frameAt(t, 200)!!.alpha, 0.01f) // peak at the midpoint
    }
    @Test fun easing_changes_the_curve_but_not_the_ends() {
        val e = o(inA = OverlayAnimation(AnimationKind.FADE, 1000, OverlayEasing.EASE_IN))
        assertEquals(0.25f, OverlayAnimator.frameAt(e, 1500)!!.alpha, 0.001f)
        val x = o(inA = OverlayAnimation(AnimationKind.FADE, 1000, OverlayEasing.EASE_OUT))
        assertEquals(0.75f, OverlayAnimator.frameAt(x, 1500)!!.alpha, 0.001f)
        val b = o(inA = OverlayAnimation(AnimationKind.FADE, 1000, OverlayEasing.EASE_IN_OUT))
        assertEquals(0.5f, OverlayAnimator.frameAt(b, 1500)!!.alpha, 0.001f)
    }
    @Test fun an_unknown_kind_does_not_move_the_overlay() =
        assertEquals(OverlayFrame.Still, OverlayAnimator.frameAt(o(inA = lin(AnimationKind("future"))), 1000))
}
