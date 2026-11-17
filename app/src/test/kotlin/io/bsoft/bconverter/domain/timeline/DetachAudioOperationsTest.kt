package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetachAudioOperationsTest {
    private val base = TL.timeline(TL.video("a", 10_000), TL.video("b", 8_000, sourceStart = 2_000, sourceDuration = 20_000))

    @Test fun detaching_mutes_the_clip_and_adds_the_same_slice_as_audio_at_the_same_time() {
        val t = ok(TimelineOperations.detachAudio(base, "b", "b-audio", "lane-new"))
        assertTrue(t.videoClips[1].audioMuted); assertFalse(t.videoClips[0].audioMuted)
        val audio = t.audioClips.single()
        assertEquals("b-audio", audio.id)
        assertEquals(t.videoClips[1].source, audio.source)
        assertEquals(20_000L, audio.sourceDurationMs); assertEquals(2_000L, audio.sourceStartMs); assertEquals(10_000L, audio.sourceEndMs)
        assertEquals(10_000L, audio.startTimeMs); assertEquals(1f, audio.volume, 0f)
        assertEquals(18_000L, t.videoDurationMs)
    }

    @Test fun it_joins_a_free_lane_or_opens_a_new_one() {
        val withMusic = TL.withAudio(base, listOf(TL.audio("m", 5_000, start = 0)))
        val free = ok(TimelineOperations.detachAudio(withMusic, "b", "x", "new"))
        assertEquals(1, free.audioTracks.size)
        val busy = ok(TimelineOperations.detachAudio(withMusic, "a", "x", "new"))
        assertEquals(2, busy.audioTracks.size); assertEquals("new", busy.audioTracks[1].id)
    }

    @Test fun a_sped_up_clip_gives_audio_with_the_same_speed_and_length() {
        val fast = ok(TimelineOperations.changeSpeed(base, "a", 2f))
        val t = ok(TimelineOperations.detachAudio(fast, "a", "x", "n"))
        assertEquals(2f, t.audioClips.single().playbackSpeed, 0f)
        assertEquals(t.videoClips[0].durationMs, t.audioClips.single().durationMs)
    }

    @Test fun an_already_detached_clip_cannot_be_detached_again() {
        val once = ok(TimelineOperations.detachAudio(base, "a", "x", "n"))
        assertEquals(EditError.AlreadyDetached, err(TimelineOperations.detachAudio(once, "a", "y", "n2")))
    }

    @Test fun other_clip_kinds_unknown_clips_and_taken_ids_are_refused() {
        val withMusic = TL.withAudio(base, listOf(TL.audio("m")))
        assertEquals(EditError.WrongClipType, err(TimelineOperations.detachAudio(withMusic, "m", "x", "n")))
        assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.detachAudio(base, "zz", "x", "n")))
        assertEquals(EditError.DuplicateId, err(TimelineOperations.detachAudio(base, "a", "b", "n")))
    }

    @Test fun splitting_a_muted_clip_keeps_both_halves_muted() {
        val t = ok(TimelineOperations.detachAudio(base, "a", "x", "n"))
        val split = ok(TimelineOperations.split(t, "a", 4_000, "a2"))
        assertTrue(split.videoClips[0].audioMuted && split.videoClips[1].audioMuted)
    }

    @Test fun the_editor_makes_it_one_undoable_step() {
        val e = DefaultTimelineEditor(base, io.bsoft.bconverter.fakes.SequentialIds())
        assertTrue(e.detachAudio("a") is EditResult.Success)
        assertTrue(e.state.value.timeline.videoClips[0].audioMuted); assertEquals(1, e.state.value.timeline.audioClips.size)
        e.undo()
        assertFalse(e.state.value.timeline.videoClips[0].audioMuted); assertEquals(0, e.state.value.timeline.audioClips.size)
        e.redo()
        assertEquals("id-1", e.state.value.timeline.audioClips.single().id)
    }
}
