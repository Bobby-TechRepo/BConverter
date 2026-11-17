package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipMotionTest {
    private fun clip(motion: PipMotion? = null) = PipClip("p", TL.source("p"), 9_000, 0, 4_000, 2_000, PipLayout(centerX = 0.2f, centerY = 0.25f), motion = motion)
    private val withPip = ok(TimelineOperations.addPip(TL.timeline(TL.video("a", 10_000)), clip()))

    @Test fun without_a_motion_the_centre_stays_where_the_layout_puts_it() {
        assertEquals(0.2f to 0.25f, PipPath.centerAt(clip(), 4_000))
    }
    @Test fun a_linear_glide_is_at_the_start_the_middle_and_the_end() {
        val c = clip(PipMotion(0.8f, 0.75f, eased = false))
        assertEquals(0.2f to 0.25f, PipPath.centerAt(c, 2_000)); assertEquals(0.5f to 0.5f, PipPath.centerAt(c, 4_000))
        val end = PipPath.centerAt(c, 6_000); assertEquals(0.8f, end.first, 0.0001f); assertEquals(0.75f, end.second, 0.0001f)
    }
    @Test fun an_eased_glide_starts_and_ends_slowly() {
        val c = clip(PipMotion(0.8f, 0.25f, eased = true))
        assertTrue(PipPath.centerAt(c, 2_500).first < 0.2f + 0.6f * 0.25f * 0.5f)
        assertEquals(0.5f, PipPath.centerAt(c, 4_000).first, 0.0001f)
        assertTrue(PipPath.centerAt(c, 5_500).first > 0.8f - 0.6f * 0.25f * 0.5f)
    }
    @Test fun before_and_after_the_clip_the_centre_is_clamped_to_the_ends() {
        val c = clip(PipMotion(0.8f, 0.25f, eased = false))
        assertEquals(0.2f, PipPath.centerAt(c, 0).first, 0f); assertEquals(0.8f, PipPath.centerAt(c, 99_000).first, 0.0001f)
    }
    @Test fun an_end_outside_the_frame_is_not_a_motion() {
        assertTrue(runCatching { PipMotion(1.1f, 0.5f) }.isFailure); assertTrue(runCatching { PipMotion(0.5f, -0.1f) }.isFailure); assertTrue(runCatching { PipMotion(Float.NaN, 0.5f) }.isFailure)
    }
    @Test fun a_motion_can_be_set_and_cleared() {
        val moved = ok(TimelineOperations.setPipMotion(withPip, "p", PipMotion(0.9f, 0.9f)))
        assertEquals(PipMotion(0.9f, 0.9f), moved.pipClips.single().motion)
        assertNull(ok(TimelineOperations.setPipMotion(moved, "p", null)).pipClips.single().motion)
    }
    @Test fun the_motion_of_an_unknown_clip_is_refused() { assertTrue(TimelineOperations.setPipMotion(withPip, "zz", PipMotion(0.5f, 0.5f)) is EditResult.Failure) }
    @Test fun moving_the_layout_carries_the_end_point_along() {
        val moved = ok(TimelineOperations.setPipMotion(withPip, "p", PipMotion(0.6f, 0.5f)))
        val c = ok(TimelineOperations.setPipLayout(moved, "p", PipLayout(centerX = 0.3f, centerY = 0.35f))).pipClips.single()
        assertEquals(0.7f, c.motion!!.endX, 0.0001f); assertEquals(0.6f, c.motion!!.endY, 0.0001f)
    }
    @Test fun the_end_point_stays_inside_the_frame_when_the_start_is_moved() {
        val moved = ok(TimelineOperations.setPipMotion(withPip, "p", PipMotion(0.95f, 0.5f)))
        assertEquals(1f, ok(TimelineOperations.setPipLayout(moved, "p", PipLayout(centerX = 0.5f, centerY = 0.25f))).pipClips.single().motion!!.endX, 0f)
    }
    @Test fun without_an_end_width_the_size_stays() { assertEquals(0.35f, PipPath.widthAt(clip(PipMotion(0.8f, 0.25f)), 4_000), 0f) }
    @Test fun the_width_follows_the_glide() {
        val c = clip(PipMotion(0.8f, 0.25f, eased = false, endWidth = 0.75f))
        val w0 = c.layout.width
        assertEquals(w0, PipPath.widthAt(c, 2_000), 0.0001f); assertEquals((w0 + 0.75f) / 2, PipPath.widthAt(c, 4_000), 0.0001f); assertEquals(0.75f, PipPath.widthAt(c, 9_000), 0.0001f)
    }
    @Test fun an_end_width_outside_the_limits_is_not_a_motion() {
        assertTrue(runCatching { PipMotion(0.5f, 0.5f, endWidth = 0.05f) }.isFailure); assertTrue(runCatching { PipMotion(0.5f, 0.5f, endWidth = 1.5f) }.isFailure)
    }
    @Test fun a_trim_keeps_the_motion() {
        val moved = ok(TimelineOperations.setPipMotion(withPip, "p", PipMotion(0.9f, 0.9f)))
        assertEquals(PipMotion(0.9f, 0.9f), ok(TimelineOperations.trimPip(moved, "p", 0, 2_000)).pipClips.single().motion)
    }
}
