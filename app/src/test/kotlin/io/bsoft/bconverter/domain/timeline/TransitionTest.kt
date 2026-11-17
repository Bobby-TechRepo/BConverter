package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fades between clips: the model, how it changes the timeline's length, and what is really applied when clips are short. */
class TransitionTest {
    private fun fade(ms: Long = 1_000) = Transition(TransitionType.FADE_BLACK, ms)
    private fun cross(ms: Long = 1_000) = Transition(TransitionType.CROSSFADE, ms)
    private fun clips(second: Transition, secondSpan: Long = 10_000, firstSpan: Long = 10_000) =
        Timeline(listOf(TL.video("a", firstSpan), TL.video("b", secondSpan).copy(transitionIn = second))).normalized()

    @Test fun a_transition_lasts_between_a_quarter_second_and_two_seconds() {
        Transition(TransitionType.CROSSFADE, Transition.MIN_MS); Transition(TransitionType.CROSSFADE, Transition.MAX_MS)
        assertTrue(runCatching { Transition(TransitionType.CROSSFADE, Transition.MIN_MS - 1) }.isFailure)
        assertTrue(runCatching { Transition(TransitionType.CROSSFADE, Transition.MAX_MS + 1) }.isFailure)
    }

    @Test fun clips_have_no_transition_by_default() {
        assertEquals(Transition.None, TL.video("a").transitionIn)
        assertTrue(Transition.None.isNone)
    }

    @Test fun a_fade_through_black_does_not_change_the_length_or_the_clip_positions() {
        val t = clips(fade())
        assertEquals(20_000L, t.videoDurationMs); assertEquals(10_000L, t.videoClips[1].startTimeMs)
    }

    @Test fun a_crossfade_overlaps_the_clips_and_shortens_the_timeline_by_its_length() {
        val t = clips(cross(1_000))
        assertEquals(19_000L, t.videoDurationMs); assertEquals(9_000L, t.videoClips[1].startTimeMs); assertEquals(19_000L, t.videoClips[1].endTimeMs)
    }

    @Test fun every_crossfade_in_a_row_overlaps() {
        val t = Timeline(
            listOf(TL.video("a", 10_000), TL.video("b", 10_000).copy(transitionIn = cross(2_000)), TL.video("c", 10_000).copy(transitionIn = cross(1_000))),
        ).normalized()
        assertEquals(listOf(0L, 8_000L, 17_000L), t.videoClips.map { it.startTimeMs }); assertEquals(27_000L, t.videoDurationMs)
    }

    @Test fun the_first_clip_never_has_a_transition() {
        val t = Timeline(listOf(TL.video("a", 10_000).copy(transitionIn = cross()))).normalized()
        assertEquals(Transition.None, t.videoClips[0].transitionIn); assertEquals(10_000L, t.videoDurationMs)
        assertEquals(TransitionType.NONE, t.joinBefore(0).type)
    }

    @Test fun a_crossfade_is_limited_to_half_of_the_shorter_clip() {
        val t = clips(cross(2_000), secondSpan = 1_500)
        assertEquals(750L, t.joinBefore(1).durationMs); assertEquals(1_500L + 10_000L - 750L, t.videoDurationMs)
    }

    @Test fun a_fade_through_black_is_limited_to_the_shorter_clip() {
        assertEquals(1_200L, clips(fade(2_000), secondSpan = 1_200).joinBefore(1).durationMs)
        assertEquals(2_000L, clips(fade(2_000)).joinBefore(1).durationMs)
    }

    @Test fun the_join_reports_its_type_and_length() {
        assertEquals(JoinEffect(TransitionType.CROSSFADE, 1_000), clips(cross(1_000)).joinBefore(1))
        assertEquals(JoinEffect(TransitionType.NONE, 0), clips(Transition.None).joinBefore(1))
        assertFalse(clips(Transition.None).joinBefore(1).isActive)
        assertTrue(clips(fade()).joinBefore(1).isActive)
    }

    @Test fun the_overlap_only_exists_for_crossfades() {
        assertEquals(0L, clips(fade()).overlapBefore(1)); assertEquals(1_000L, clips(cross(1_000)).overlapBefore(1))
    }

    @Test fun the_clip_playing_in_an_overlap_is_the_later_one() {
        val t = clips(cross(1_000))
        assertEquals("a", t.videoClipAt(8_999)?.id); assertEquals("b", t.videoClipAt(9_000)?.id); assertEquals("b", t.videoClipAt(9_500)?.id)
    }
}
