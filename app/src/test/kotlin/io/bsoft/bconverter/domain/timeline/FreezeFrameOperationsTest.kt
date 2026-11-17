package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeFrameOperationsTest {
    private val base = TL.withAudio(TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000)), listOf(TL.audio("m")))
    private fun freeze(t: Timeline = base, clip: String = "a", at: Long = 4_000, ms: Long = 2_000) = TimelineOperations.insertFreeze(t, clip, at, "f", "a2", ms)

    @Test fun the_clip_is_split_and_a_freeze_goes_between_the_halves() {
        val c = ok(freeze()).videoClips
        assertEquals(listOf("a", "f", "a2", "b"), c.map { it.id })
        assertEquals(listOf(false, true, false, false), c.map { it.isFreeze })
    }
    @Test fun the_timeline_grows_by_the_freeze_length() { assertEquals(22_000L, ok(freeze()).videoDurationMs) }
    @Test fun the_halves_cover_the_original_source_without_a_gap() {
        val c = ok(freeze()).videoClips
        assertEquals(0L to 4_000L, c[0].sourceStartMs to c[0].sourceEndMs); assertEquals(4_000L to 10_000L, c[2].sourceStartMs to c[2].sourceEndMs)
    }
    @Test fun the_freeze_shows_the_picture_at_the_playhead() {
        val f = ok(freeze()).videoClips[1]
        assertEquals(4_000L, f.sourceStartMs); assertTrue(f.sourceEndMs > f.sourceStartMs); assertEquals(2_000L, f.durationMs); assertEquals(1f, f.playbackSpeed, 0f)
    }
    @Test fun the_freeze_is_silent_and_keeps_the_clips_look_and_framing() {
        val styled = ok(TimelineOperations.setTransform(base, "a", io.bsoft.bconverter.domain.transform.ClipTransform(rotationDegrees = 90)))
        val f = ok(freeze(styled)).videoClips[1]
        assertTrue(f.audioMuted); assertEquals(90, f.transform.rotationDegrees)
    }
    @Test fun the_freeze_near_the_end_of_a_clip_still_has_picture_to_read() {
        val f = ok(freeze(at = 9_950)).videoClips[1]
        assertTrue(f.sourceStartMs <= 9_900 + 1); assertTrue(f.sourceEndMs <= 10_000)
    }
    @Test fun a_transition_stays_before_the_first_half_only() {
        val t = ok(TimelineOperations.setTransition(base, "b", Transition(TransitionType.FADE_BLACK, 1_000)))
        val c = ok(freeze(t, clip = "b", at = 14_000)).videoClips
        assertEquals(listOf(true, false, true, true), c.map { it.transitionIn.isNone })
    }
    @Test fun the_playhead_must_be_inside_the_clip() {
        assertEquals(EditError.SplitOutsideClip, err(freeze(at = 0))); assertEquals(EditError.SplitOutsideClip, err(freeze(at = 10_000)))
    }
    @Test fun the_length_must_be_in_range() {
        assertEquals(EditError.InvalidTransition, err(freeze(ms = 100))); assertEquals(EditError.InvalidTransition, err(freeze(ms = 20_000)))
    }
    @Test fun audio_clips_and_unknown_clips_are_refused() {
        assertEquals(EditError.WrongClipType, err(freeze(clip = "m"))); assertEquals(EditError.ClipNotFound("zz"), err(freeze(clip = "zz")))
    }
    @Test fun a_taken_id_is_refused() { assertEquals(EditError.DuplicateId, err(TimelineOperations.insertFreeze(base, "a", 4_000, "b", "a2", 2_000))) }
    @Test fun a_reversed_clip_cannot_be_frozen() {
        assertEquals(EditError.NotForFreezeFrame, err(freeze(ok(TimelineOperations.setReversed(base, "a", true)))))
    }
    @Test fun a_freeze_cannot_be_frozen_trimmed_split_sped_up_or_reversed() {
        val t = ok(freeze())
        assertEquals(EditError.NotForFreezeFrame, err(TimelineOperations.insertFreeze(t, "f", 100, "g", "h", 1_000)))
        assertEquals(EditError.NotForFreezeFrame, err(TimelineOperations.trim(t, "f", 0, 100)))
        assertEquals(EditError.NotForFreezeFrame, err(TimelineOperations.split(t, "f", 1_000, "x")))
        assertEquals(EditError.NotForFreezeFrame, err(TimelineOperations.changeSpeed(t, "f", 2f)))
        assertEquals(EditError.NotForFreezeFrame, err(TimelineOperations.setReversed(t, "f", true)))
    }
    @Test fun the_length_can_be_changed() {
        val t = ok(TimelineOperations.setFreezeLength(ok(freeze()), "f", 5_000))
        assertEquals(5_000L, t.videoClips[1].durationMs); assertEquals(25_000L, t.videoDurationMs)
    }
    @Test fun only_a_freeze_has_a_length_to_change() {
        assertEquals(EditError.WrongClipType, err(TimelineOperations.setFreezeLength(base, "a", 2_000)))
        assertEquals(EditError.InvalidTransition, err(TimelineOperations.setFreezeLength(ok(freeze()), "f", 50)))
    }
    @Test fun a_sped_up_clip_is_split_at_the_right_source_time() {
        val fast = ok(TimelineOperations.changeSpeed(base, "a", 2f))
        val c = ok(freeze(fast, at = 2_000)).videoClips
        assertEquals(4_000L, c[0].sourceEndMs); assertEquals(1f, c[1].playbackSpeed, 0f)
    }
    @Test fun the_command_undoes() {
        val c = InsertFreezeCommand("a", 4_000, "f", "a2", 2_000)
        val after = (c.execute(base) as EditResult.Success).timeline
        assertEquals(4, after.videoClips.size); assertEquals(base.videoClips, c.undo(after).videoClips)
    }
    @Test fun the_editor_undoes_and_redoes() {
        val e = DefaultTimelineEditor(base)
        e.insertFreezeFrame("a", 4_000, 2_000); assertEquals(4, e.state.value.timeline.videoClips.size)
        e.undo(); assertEquals(2, e.state.value.timeline.videoClips.size)
        e.redo(); assertEquals(4, e.state.value.timeline.videoClips.size)
    }
    @Test fun the_editor_changes_a_freezes_length_with_undo() {
        val e = DefaultTimelineEditor(base); e.insertFreezeFrame("a", 4_000, 2_000)
        val id = e.state.value.timeline.videoClips[1].id
        e.setFreezeLength(id, 4_000); assertEquals(4_000L, e.state.value.timeline.videoClips[1].durationMs)
        e.undo(); assertEquals(2_000L, e.state.value.timeline.videoClips[1].durationMs)
    }
    @Test(expected = IllegalArgumentException::class) fun a_clip_cannot_hold_a_freeze_length_out_of_range() { TL.video("a", 5_000).copy(freezeMs = 100) }
}
