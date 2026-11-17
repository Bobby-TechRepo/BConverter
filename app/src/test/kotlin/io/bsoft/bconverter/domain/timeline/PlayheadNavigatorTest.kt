package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayheadNavigatorTest {
    // video a: 0..10 s, b: 10..14 s; audio m: 2..7 s (lane 0), n: 12..18 s (lane 1)
    private val timeline = TL.withAudio(
        TL.timeline(TL.video("a", 10_000), TL.video("b", 4_000)),
        listOf(TL.audio("m", 5_000, start = 2_000)), listOf(TL.audio("n", 6_000, start = 12_000)),
    )

    // ---- stepping ----
    @Test fun stepping_moves_by_the_delta() { assertEquals(6_000L, PlayheadNavigator.step(5_000, 1_000, 20_000)) }
    @Test fun stepping_backwards_moves_earlier() { assertEquals(4_000L, PlayheadNavigator.step(5_000, -1_000, 20_000)) }
    @Test fun stepping_never_goes_before_the_start() { assertEquals(0L, PlayheadNavigator.step(500, -1_000, 20_000)) }
    @Test fun stepping_never_goes_past_the_end() { assertEquals(20_000L, PlayheadNavigator.step(19_500, 1_000, 20_000)) }
    @Test fun stepping_in_an_empty_timeline_stays_at_zero() { assertEquals(0L, PlayheadNavigator.step(0, 1_000, 0)) }

    // ---- clip edges ----
    @Test fun next_edge_is_the_nearest_clip_boundary_after_the_playhead() {
        assertEquals(2_000L, PlayheadNavigator.nextEdge(timeline, 0))
        assertEquals(7_000L, PlayheadNavigator.nextEdge(timeline, 2_000))
        assertEquals(10_000L, PlayheadNavigator.nextEdge(timeline, 7_000))
        assertEquals(12_000L, PlayheadNavigator.nextEdge(timeline, 10_000))
        assertEquals(14_000L, PlayheadNavigator.nextEdge(timeline, 12_000))
        assertEquals(18_000L, PlayheadNavigator.nextEdge(timeline, 14_000))
    }
    @Test fun next_edge_is_null_at_or_after_the_last_edge() {
        assertNull(PlayheadNavigator.nextEdge(timeline, 18_000)); assertNull(PlayheadNavigator.nextEdge(timeline, 99_000))
    }
    @Test fun next_edge_from_between_edges_finds_the_following_one() { assertEquals(7_000L, PlayheadNavigator.nextEdge(timeline, 3_500)) }

    @Test fun previous_edge_is_the_nearest_clip_boundary_before_the_playhead() {
        assertEquals(14_000L, PlayheadNavigator.previousEdge(timeline, 18_000))
        assertEquals(12_000L, PlayheadNavigator.previousEdge(timeline, 14_000))
        assertEquals(10_000L, PlayheadNavigator.previousEdge(timeline, 12_000))
        assertEquals(2_000L, PlayheadNavigator.previousEdge(timeline, 7_000))
        assertEquals(0L, PlayheadNavigator.previousEdge(timeline, 2_000))
    }
    @Test fun previous_edge_is_null_at_the_start() { assertNull(PlayheadNavigator.previousEdge(timeline, 0)) }
    @Test fun previous_edge_from_between_edges_finds_the_one_before() { assertEquals(2_000L, PlayheadNavigator.previousEdge(timeline, 5_000)) }
    @Test fun an_empty_timeline_has_no_edges() {
        assertNull(PlayheadNavigator.nextEdge(Timeline(), 0)); assertNull(PlayheadNavigator.previousEdge(Timeline(), 5))
    }

    // ---- stepping through clips (video first in order, then audio by start) ----
    @Test fun next_clip_follows_the_clip_order() {
        assertEquals("b", PlayheadNavigator.adjacentClip(timeline, "a", +1))
        assertEquals("m", PlayheadNavigator.adjacentClip(timeline, "b", +1))
        assertEquals("n", PlayheadNavigator.adjacentClip(timeline, "m", +1))
    }
    @Test fun previous_clip_goes_back_through_the_order() {
        assertEquals("m", PlayheadNavigator.adjacentClip(timeline, "n", -1))
        assertEquals("b", PlayheadNavigator.adjacentClip(timeline, "m", -1))
        assertEquals("a", PlayheadNavigator.adjacentClip(timeline, "b", -1))
    }
    @Test fun there_is_no_clip_beyond_the_ends() {
        assertNull(PlayheadNavigator.adjacentClip(timeline, "n", +1)); assertNull(PlayheadNavigator.adjacentClip(timeline, "a", -1))
    }
    @Test fun with_nothing_selected_next_is_the_first_and_previous_the_last() {
        assertEquals("a", PlayheadNavigator.adjacentClip(timeline, null, +1)); assertEquals("n", PlayheadNavigator.adjacentClip(timeline, null, -1))
    }
    @Test fun an_unknown_selection_is_treated_as_none() { assertEquals("a", PlayheadNavigator.adjacentClip(timeline, "ghost", +1)) }
    @Test fun an_empty_timeline_has_no_clips_to_step_to() { assertNull(PlayheadNavigator.adjacentClip(Timeline(), null, +1)) }
}
