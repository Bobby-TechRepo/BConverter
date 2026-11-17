package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipKeyframesTest {
    private fun kf(t: Long, x: Float = 0.5f, y: Float = 0.5f, w: Float = 0.4f, o: Float = 1f) = PipKeyframe(t, x, y, w, o)
    private fun clip(keys: List<PipKeyframe> = emptyList(), motion: PipMotion? = null) = PipClip("p", TL.source("p"), 9_000, 0, 4_000, 2_000, PipLayout(0.2f, 0.3f, 0.35f, 0.8f), motion = motion, keyframes = keys)
    private val withPip = ok(TimelineOperations.addPip(TL.timeline(TL.video("a", 10_000)), clip()))

    @Test fun before_the_first_and_after_the_last_keyframe_the_look_holds() {
        val k = listOf(kf(1_000, x = 0.2f), kf(3_000, x = 0.8f))
        assertEquals(0.2f, PipKeyframes.interpolate(k, 0).x, 0f); assertEquals(0.8f, PipKeyframes.interpolate(k, 9_000).x, 0f)
    }
    @Test fun between_two_keyframes_everything_changes_in_a_straight_line() {
        val s = PipKeyframes.interpolate(listOf(kf(0, 0.2f, 0.2f, 0.2f, 0f), kf(2_000, 0.6f, 0.8f, 0.6f, 1f)), 500)
        assertEquals(0.3f, s.x, 0.0001f); assertEquals(0.35f, s.y, 0.0001f); assertEquals(0.3f, s.width, 0.0001f); assertEquals(0.25f, s.opacity, 0.0001f)
    }
    @Test fun the_look_passes_through_every_keyframe() {
        val k = listOf(kf(0, x = 0.1f), kf(1_000, x = 0.9f), kf(2_000, x = 0.3f))
        assertEquals(0.9f, PipKeyframes.interpolate(k, 1_000).x, 0f); assertEquals(0.6f, PipKeyframes.interpolate(k, 1_500).x, 0.0001f)
    }
    @Test fun an_eased_step_starts_and_ends_slowly_and_still_passes_through_the_ends() {
        val k = listOf(PipKeyframe(0, 0f, 0.5f, 0.4f, 1f, eased = true), PipKeyframe(1_000, 1f, 0.5f, 0.4f, 1f))
        assertTrue(PipKeyframes.interpolate(k, 100).x < 0.1f); assertTrue(PipKeyframes.interpolate(k, 900).x > 0.9f)
        assertEquals(0.5f, PipKeyframes.interpolate(k, 500).x, 0.0001f); assertEquals(1f, PipKeyframes.interpolate(k, 1_000).x, 0f)
    }
    @Test fun the_easing_belongs_to_the_step_that_leaves_the_keyframe() {
        val k = listOf(kf(0, x = 0f), PipKeyframe(1_000, 1f, 0.5f, 0.4f, 1f, eased = true), kf(2_000, x = 0f))
        assertEquals(0.5f, PipKeyframes.interpolate(k, 500).x, 0.0001f)   // first step is straight
        assertTrue(PipKeyframes.interpolate(k, 1_100).x > 0.9f)           // second step is eased: slow start
    }
    @Test fun a_keyframe_outside_the_frame_or_limits_is_not_a_keyframe() {
        assertTrue(runCatching { kf(0, x = 1.2f) }.isFailure); assertTrue(runCatching { kf(0, w = 0.05f) }.isFailure); assertTrue(runCatching { kf(0, o = 1.5f) }.isFailure); assertTrue(runCatching { kf(-1) }.isFailure)
    }
    @Test fun a_clip_must_have_its_keyframes_in_increasing_order() {
        assertTrue(runCatching { clip(listOf(kf(5), kf(5))) }.isFailure); assertTrue(runCatching { clip(listOf(kf(9), kf(5))) }.isFailure)
    }
    @Test fun the_state_at_a_moment_comes_from_the_keyframes_or_the_layout() {
        assertEquals(PipState(0.2f, 0.3f, 0.35f, 0.8f), PipPath.stateAt(clip(), 3_000))
        assertEquals(0.5f, PipPath.stateAt(clip(listOf(kf(0, x = 0.2f), kf(2_000, x = 0.8f))), 3_000).x, 0.0001f)
    }
    @Test fun keyframes_can_be_set_and_replace_a_glide() {
        val glide = ok(TimelineOperations.setPipMotion(withPip, "p", PipMotion(0.9f, 0.9f)))
        val c = ok(TimelineOperations.setPipKeyframes(glide, "p", listOf(kf(0), kf(1_000)))).pipClips.single()
        assertEquals(2, c.keyframes.size); assertEquals(null, c.motion)
    }
    @Test fun bad_keyframes_are_refused() {
        assertTrue(TimelineOperations.setPipKeyframes(withPip, "p", listOf(kf(5_000))) is EditResult.Failure)
        assertTrue(TimelineOperations.setPipKeyframes(withPip, "p", listOf(kf(2_000), kf(1_000))) is EditResult.Failure)
        assertTrue(TimelineOperations.setPipKeyframes(withPip, "p", (0..16).map { kf(it * 100L) }) is EditResult.Failure)
        assertTrue(TimelineOperations.setPipKeyframes(withPip, "zz", emptyList()) is EditResult.Failure)
    }
    @Test fun the_first_keyframe_added_takes_the_clips_present_look() {
        val k = PipKeyframes.addAt(clip(), 0)
        assertEquals(listOf(PipKeyframe(0, 0.2f, 0.3f, 0.35f, 0.8f)), k)
    }
    @Test fun adding_later_also_keeps_the_start_and_takes_the_look_there() {
        val k = PipKeyframes.addAt(clip(), 1_500)
        assertEquals(listOf(0L, 1_500L), k.map { it.offsetMs }); assertEquals(k[0].state, k[1].state)
    }
    @Test fun a_glide_becomes_its_two_end_keyframes() {
        val k = PipKeyframes.addAt(clip(motion = PipMotion(0.8f, 0.7f, endWidth = 0.6f)), 1_000)
        assertEquals(listOf(0L, 1_000L, 4_000L), k.map { it.offsetMs }); assertEquals(0.8f, k.last().x, 0f); assertEquals(0.6f, k.last().width, 0f)
        assertEquals(0.2f + (0.8f - 0.2f) * 0.25f, k[1].x, 0.0001f)
    }
    @Test fun a_keyframe_is_not_added_outside_the_clip_or_twice_or_past_the_limit() {
        val c = clip(listOf(kf(0), kf(1_000)))
        assertEquals(c.keyframes, PipKeyframes.addAt(c, 5_000)); assertEquals(c.keyframes, PipKeyframes.addAt(c, 1_000)); assertEquals(c.keyframes, PipKeyframes.addAt(c, -1))
        val full = clip((0 until 16).map { kf(it * 100L) }); assertEquals(full.keyframes, PipKeyframes.addAt(full, 3_950))
    }
    @Test fun a_look_given_at_a_moment_adds_a_keyframe_there_and_keeps_the_order() {
        val c = clip(listOf(kf(0), kf(3_000)))
        val k = PipKeyframes.withLookAt(c, 1_000, PipState(0.9f, 0.1f, 0.5f, 0.3f))
        assertEquals(listOf(0L, 1_000L, 3_000L), k.map { it.offsetMs }); assertEquals(PipKeyframe(1_000, 0.9f, 0.1f, 0.5f, 0.3f), k[1])
    }
    @Test fun a_look_given_near_a_keyframe_edits_that_keyframe_and_keeps_its_time_and_easing() {
        val c = clip(listOf(PipKeyframe(1_000, 0.5f, 0.5f, 0.4f, 1f, eased = true), kf(3_000)))
        val k = PipKeyframes.withLookAt(c, 1_040, PipState(0.2f, 0.3f, 0.4f, 1f))
        assertEquals(2, k.size); assertEquals(PipKeyframe(1_000, 0.2f, 0.3f, 0.4f, 1f, eased = true), k[0])
        assertEquals(3, PipKeyframes.withLookAt(c, 1_100, PipState(0.2f, 0.3f, 0.4f, 1f)).size)
    }
    @Test fun a_dragged_look_is_kept_inside_the_frame_and_the_size_limits() {
        val k = PipKeyframes.withLookAt(clip(listOf(kf(0))), 500, PipState(1.4f, -0.2f, 3f, 2f))
        assertEquals(PipKeyframe(500, 1f, 0f, PipLayout.MAX_WIDTH, 1f), k[1])
    }
    @Test fun a_look_is_not_added_outside_the_clip_or_past_the_limit() {
        val c = clip(listOf(kf(0)))
        assertEquals(c.keyframes, PipKeyframes.withLookAt(c, 9_000, PipState(0.5f, 0.5f, 0.4f, 1f)))
        val full = clip((0 until 16).map { kf(it * 100L) }); assertEquals(full.keyframes, PipKeyframes.withLookAt(full, 1_950, PipState(0.5f, 0.5f, 0.4f, 1f)))
    }
    @Test fun a_keyframe_can_be_replaced_keeping_its_time_and_removed() {
        val k = listOf(kf(0), kf(1_000))
        assertEquals(1_000L, PipKeyframes.replace(k, 1, kf(7, x = 0.9f)).last().offsetMs); assertEquals(0.9f, PipKeyframes.replace(k, 1, kf(7, x = 0.9f)).last().x, 0f)
        assertEquals(listOf(k[1]), PipKeyframes.remove(k, 0))
    }
    @Test fun a_trim_or_a_speedup_drops_the_keyframes_that_no_longer_fit() {
        val keyed = ok(TimelineOperations.setPipKeyframes(withPip, "p", listOf(kf(0), kf(3_000))))
        assertEquals(1, ok(TimelineOperations.trimPip(keyed, "p", 0, 2_000)).pipClips.single().keyframes.size)
        assertEquals(1, ok(TimelineOperations.setPipSpeed(keyed, "p", 2f)).pipClips.single().keyframes.size)
        assertEquals(2, ok(TimelineOperations.setPipSpeed(keyed, "p", 0.5f)).pipClips.single().keyframes.size)
    }
}
