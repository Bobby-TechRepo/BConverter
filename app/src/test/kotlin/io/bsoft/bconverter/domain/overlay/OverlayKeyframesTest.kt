package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayKeyframesTest {
    private fun kf(t: Long, x: Float = 0.5f, y: Float = 0.5f, z: Float = 1f, o: Float = 1f) = OverlayKeyframe(t, x, y, z, o)
    private val a = TextOverlay("a", "A", 1_000, 3_000, centerX = 0.3f, centerY = 0.6f)
    private val list = listOf(a)
    private fun ok(e: OverlayEdit) = (e as OverlayEdit.Ok).overlays

    @Test fun the_look_holds_before_the_first_and_after_the_last_keyframe() {
        val k = listOf(kf(500, x = 0.2f), kf(1_500, x = 0.8f))
        assertEquals(0.2f, OverlayKeyframes.interpolate(k, 0).x, 0f); assertEquals(0.8f, OverlayKeyframes.interpolate(k, 9_000).x, 0f)
    }
    @Test fun between_keyframes_everything_changes_in_a_straight_line() {
        val l = OverlayKeyframes.interpolate(listOf(kf(0, 0.2f, 0.2f, 0.5f, 0f), kf(2_000, 0.6f, 0.8f, 1.5f, 1f)), 500)
        assertEquals(0.3f, l.x, 0.0001f); assertEquals(0.35f, l.y, 0.0001f); assertEquals(0.75f, l.zoom, 0.0001f); assertEquals(0.25f, l.opacity, 0.0001f)
    }
    @Test fun an_eased_step_starts_and_ends_slowly() {
        val k = listOf(OverlayKeyframe(0, 0f, 0.5f, eased = true), OverlayKeyframe(1_000, 1f, 0.5f))
        assertTrue(OverlayKeyframes.interpolate(k, 100).x < 0.1f); assertTrue(OverlayKeyframes.interpolate(k, 900).x > 0.9f); assertEquals(0.5f, OverlayKeyframes.interpolate(k, 500).x, 0.0001f)
    }
    @Test fun bad_keyframes_are_not_keyframes() {
        assertTrue(runCatching { kf(-1) }.isFailure); assertTrue(runCatching { kf(0, z = 3f) }.isFailure); assertTrue(runCatching { kf(0, z = 0.01f) }.isFailure); assertTrue(runCatching { kf(0, o = 2f) }.isFailure)
        assertTrue(runCatching { kf(0, x = Float.NaN) }.isFailure)
    }
    @Test fun an_overlay_must_have_its_keyframes_in_increasing_order() {
        assertTrue(runCatching { a.copy(keyframes = listOf(kf(5), kf(5))) }.isFailure)
        assertTrue(runCatching { a.copy(keyframes = (0..16).map { kf(it * 10L) }) }.isFailure)
    }
    @Test fun the_first_keyframe_is_the_overlay_as_it_stands() {
        assertEquals(listOf(OverlayKeyframe(0, 0.3f, 0.6f, 1f, 1f)), OverlayKeyframes.addAt(a, 0))
    }
    @Test fun a_later_one_keeps_the_start_and_takes_the_look_there() {
        val k = OverlayKeyframes.addAt(a, 1_000)
        assertEquals(listOf(0L, 1_000L), k.map { it.offsetMs }); assertEquals(k[0].copy(offsetMs = 1_000), k[1])
    }
    @Test fun nothing_is_added_outside_the_overlay_or_twice_or_past_the_limit() {
        val k = a.copy(keyframes = listOf(kf(0), kf(1_000)))
        assertEquals(k.keyframes, OverlayKeyframes.addAt(k, 5_000)); assertEquals(k.keyframes, OverlayKeyframes.addAt(k, 1_000)); assertEquals(k.keyframes, OverlayKeyframes.addAt(k, -1))
        val full = a.copy(endMs = 9_000, keyframes = (0 until 16).map { kf(it * 100L) }); assertEquals(full.keyframes, OverlayKeyframes.addAt(full, 1_950))
    }
    @Test fun a_keyframe_can_be_replaced_keeping_its_time_and_removed() {
        val k = listOf(kf(0), kf(1_000))
        assertEquals(1_000L, OverlayKeyframes.replace(k, 1, kf(7, x = 0.9f)).last().offsetMs); assertEquals(0.9f, OverlayKeyframes.replace(k, 1, kf(7, x = 0.9f)).last().x, 0f)
        assertEquals(listOf(k[1]), OverlayKeyframes.remove(k, 0))
    }
    @Test fun a_look_given_at_a_moment_adds_or_edits_a_keyframe() {
        val c = a.copy(keyframes = listOf(OverlayKeyframe(0, 0.3f, 0.6f, 1f, 1f, eased = true), kf(1_500)))
        val added = OverlayKeyframes.withLookAt(c, 700, OverlayLook(0.9f, 0.1f, 1.5f, 0.5f))
        assertEquals(listOf(0L, 700L, 1_500L), added.map { it.offsetMs }); assertEquals(OverlayKeyframe(700, 0.9f, 0.1f, 1.5f, 0.5f), added[1])
        val edited = OverlayKeyframes.withLookAt(c, 30, OverlayLook(0.9f, 0.1f, 1f, 1f))
        assertEquals(2, edited.size); assertEquals(OverlayKeyframe(0, 0.9f, 0.1f, 1f, 1f, eased = true), edited[0])
    }
    @Test fun a_look_is_not_added_outside_the_overlay_or_past_the_limit_and_is_kept_in_range() {
        assertEquals(emptyList<OverlayKeyframe>(), OverlayKeyframes.withLookAt(a, 9_000, OverlayLook(0.5f, 0.5f, 1f, 1f)))
        assertEquals(OverlayKeyframe(0, 1f, 0f, OverlayKeyframes.MAX_ZOOM, 1f), OverlayKeyframes.withLookAt(a, 0, OverlayLook(2f, -1f, 9f, 3f)).single())
    }
    @Test fun the_overlay_is_found_where_its_keyframes_put_it() {
        val c = a.copy(keyframes = listOf(kf(0, 0.2f, 0.2f), kf(1_000, 0.8f, 0.8f, 2f)))
        val p = OverlayKeyframes.positioned(c, 1_500)  // halfway
        assertEquals(0.5f, p.centerX, 0.0001f); assertEquals(1.5f, p.scale, 0.0001f); assertEquals(a, OverlayKeyframes.positioned(a, 1_500))
    }
    @Test fun the_editor_sets_keyframes_and_refuses_bad_ones() {
        assertEquals(2, ok(TextOverlayEditor.setKeyframes(list, "a", listOf(kf(0), kf(1_000)))).single().keyframes.size)
        assertEquals(OverlayProblem.InvalidValue, (TextOverlayEditor.setKeyframes(list, "a", listOf(kf(5_000))) as OverlayEdit.Rejected).reason)
        assertEquals(OverlayProblem.InvalidValue, (TextOverlayEditor.setKeyframes(list, "a", listOf(kf(5), kf(5))) as OverlayEdit.Rejected).reason)
        assertEquals(OverlayProblem.NoSuchOverlay, (TextOverlayEditor.setKeyframes(list, "zz", emptyList()) as OverlayEdit.Rejected).reason)
    }
    @Test fun shortening_an_overlay_drops_the_keyframes_past_the_end() {
        val keyed = ok(TextOverlayEditor.setKeyframes(list, "a", listOf(kf(0), kf(1_800))))
        assertEquals(1, ok(TextOverlayEditor.retime(keyed, "a", 1_000, 2_000)).single().keyframes.size)
        assertEquals(2, ok(TextOverlayEditor.retime(keyed, "a", 1_000, 4_000)).single().keyframes.size)
    }
    @Test fun the_preview_look_follows_the_keyframes() {
        val o = a.copy(keyframes = listOf(kf(0, 0.3f, 0.6f, 1f, 1f), kf(1_000, 0.8f, 0.6f, 2f, 0f)))
        val f = OverlayAnimator.frameAt(o, 1_500)!!   // halfway
        assertEquals(0.25f, f.offsetX, 0.0001f); assertEquals(0f, f.offsetY, 0.0001f); assertEquals(1.5f, f.scale, 0.0001f); assertEquals(0.5f, f.alpha, 0.0001f)
    }
    @Test fun keyframes_combine_with_the_in_animation() {
        val o = a.copy(animIn = OverlayAnimation(AnimationKind.FADE, 1_000, OverlayEasing.LINEAR), keyframes = listOf(kf(0, 0.3f, 0.6f, 1f, 0.5f)))
        assertEquals(0.25f, OverlayAnimator.frameAt(o, 1_500)!!.alpha, 0.0001f)
    }
}
