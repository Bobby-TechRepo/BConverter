package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** How transitions reach the preview: the player plays clips one after another, so crossfaded clips are cut where the next begins. */
class PreviewTransitionTest {
    private fun cross(ms: Long = 1_000) = Transition(TransitionType.CROSSFADE, ms)
    private fun fade(ms: Long = 1_000) = Transition(TransitionType.FADE_BLACK, ms)
    private fun two(second: Transition) = Timeline(listOf(TL.video("a", 10_000), TL.video("b", 10_000).copy(transitionIn = second))).normalized()

    // ---- segments ----
    @Test fun a_crossfade_cuts_the_first_clip_where_the_second_begins() {
        val s = PreviewMapper.segments(two(cross(1_000)))
        assertEquals(9_000L, s[0].sourceEndMs); assertEquals(0L, s[0].sourceStartMs); assertEquals(9_000L, s[1].timelineStartMs)
    }

    @Test fun the_cut_is_in_source_time_so_speed_is_taken_into_account() {
        val t = Timeline(listOf(TL.video("a", 10_000, speed = 2f), TL.video("b", 10_000).copy(transitionIn = cross(1_000)))).normalized()
        // a lasts 5 s on the timeline; the last second of it is overlapped, i.e. 2 s of source.
        assertEquals(8_000L, PreviewMapper.segments(t)[0].sourceEndMs)
    }

    @Test fun a_fade_through_black_and_plain_cuts_leave_the_segments_whole() {
        assertEquals(10_000L, PreviewMapper.segments(two(fade()))[0].sourceEndMs)
        assertEquals(10_000L, PreviewMapper.segments(two(Transition.None))[0].sourceEndMs)
    }

    @Test fun the_last_clip_is_never_cut() {
        assertEquals(10_000L, PreviewMapper.segments(two(cross()))[1].sourceEndMs)
    }

    // ---- position ----
    @Test fun inside_a_crossfade_the_later_clip_is_the_one_previewed() {
        val p = PreviewMapper.positionAt(two(cross(1_000)), 9_500)!!
        assertEquals("b", p.clip.id); assertEquals(500L, p.sourcePositionMs)
    }

    @Test fun just_before_the_overlap_it_is_still_the_first_clip() {
        assertEquals("a", PreviewMapper.positionAt(two(cross(1_000)), 8_999)!!.clip.id)
    }

    // ---- black for a fade through black ----
    @Test fun a_fade_through_black_darkens_to_full_black_at_the_cut_and_back() {
        val t = two(fade(1_000))
        assertEquals(0f, TransitionPreview.blackAlpha(t, 9_000), 0.001f)
        assertEquals(0.5f, TransitionPreview.blackAlpha(t, 9_750), 0.001f)
        assertEquals(1f, TransitionPreview.blackAlpha(t, 10_000), 0.001f)
        assertEquals(0.5f, TransitionPreview.blackAlpha(t, 10_250), 0.001f)
        assertEquals(0f, TransitionPreview.blackAlpha(t, 10_500), 0.001f)
    }

    @Test fun elsewhere_and_for_other_transitions_nothing_is_darkened() {
        assertEquals(0f, TransitionPreview.blackAlpha(two(fade()), 3_000), 0f)
        assertEquals(0f, TransitionPreview.blackAlpha(two(cross()), 9_500), 0f)
        assertEquals(0f, TransitionPreview.blackAlpha(two(Transition.None), 10_000), 0f)
        assertEquals(0f, TransitionPreview.blackAlpha(Timeline(), 0), 0f)
    }

    @Test fun a_short_clip_shortens_the_fade() {
        val t = Timeline(listOf(TL.video("a", 10_000), TL.video("b", 600).copy(transitionIn = fade(2_000)))).normalized()
        assertEquals(0f, TransitionPreview.blackAlpha(t, 9_699), 0.001f)
        assertEquals(0.5f, TransitionPreview.blackAlpha(t, 9_850), 0.01f)
    }
}
