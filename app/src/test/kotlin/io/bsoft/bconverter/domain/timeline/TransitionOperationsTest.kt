package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionOperationsTest {
    private val base = TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000))
    private val cross = Transition(TransitionType.CROSSFADE, 1_000)

    @Test fun a_transition_is_set_on_the_cut_before_a_clip() {
        val t = ok(TimelineOperations.setTransition(base, "b", cross))
        assertEquals(cross, t.videoClips[1].transitionIn); assertEquals(Transition.None, t.videoClips[2].transitionIn)
        assertEquals(29_000L, t.videoDurationMs)
    }

    @Test fun it_can_be_replaced_and_removed() {
        val once = ok(TimelineOperations.setTransition(base, "b", cross))
        val fade = Transition(TransitionType.FADE_BLACK, 500)
        assertEquals(fade, ok(TimelineOperations.setTransition(once, "b", fade)).videoClips[1].transitionIn)
        val none = ok(TimelineOperations.setTransition(once, "b", Transition.None))
        assertEquals(30_000L, none.videoDurationMs)
    }

    @Test fun the_first_clip_has_nothing_before_it() {
        assertEquals(EditError.NoPreviousClip, err(TimelineOperations.setTransition(base, "a", cross)))
    }

    @Test fun only_video_clips_and_known_clips_accept_one() {
        val withAudio = TL.withAudio(base, listOf(TL.audio("m")))
        assertEquals(EditError.WrongClipType, err(TimelineOperations.setTransition(withAudio, "m", cross)))
        assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.setTransition(base, "zz", cross)))
    }

    @Test fun splitting_keeps_the_transition_on_the_first_half_only() {
        val t = ok(TimelineOperations.setTransition(base, "b", cross))
        val split = ok(TimelineOperations.split(t, "b", 14_000, "b2"))
        assertEquals(cross, split.videoClips[1].transitionIn); assertEquals(Transition.None, split.videoClips[2].transitionIn)
    }

    @Test fun removing_a_clip_leaves_the_transition_of_the_next_clip_in_place() {
        val t = ok(TimelineOperations.setTransition(ok(TimelineOperations.setTransition(base, "b", cross)), "c", Transition(TransitionType.FADE_BLACK, 500)))
        val without = ok(TimelineOperations.remove(t, "b"))
        assertEquals(Transition(TransitionType.FADE_BLACK, 500), without.videoClips[1].transitionIn)
    }

    @Test fun a_clip_moved_to_the_front_loses_its_transition() {
        val t = ok(TimelineOperations.setTransition(base, "b", cross))
        assertEquals(Transition.None, ok(TimelineOperations.moveVideo(t, "b", 0)).videoClips[0].transitionIn)
    }

    @Test fun shortening_a_clip_shrinks_a_crossfade_that_no_longer_fits() {
        val t = ok(TimelineOperations.setTransition(base, "b", Transition(TransitionType.CROSSFADE, 2_000)))
        val trimmed = ok(TimelineOperations.trim(t, "b", 0, 1_000))
        assertEquals(500L, trimmed.joinBefore(1).durationMs)
    }
}
