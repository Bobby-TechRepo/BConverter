package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipSoundOperationsTest {
    private val base = TL.withAudio(TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000)), listOf(TL.audio("m")))
    private val shaped = ClipSound(0.5f, 1_000, 2_000)

    @Test fun a_video_clips_own_sound_is_shaped() {
        val c = ok(TimelineOperations.setSound(base, "b", shaped)).videoClips
        assertEquals(listOf(1f, 0.5f), c.map { it.volume }); assertEquals(listOf(0L, 1_000L), c.map { it.fadeInMs }); assertEquals(listOf(0L, 2_000L), c.map { it.fadeOutMs })
    }
    @Test fun an_audio_clip_is_shaped() {
        val a = ok(TimelineOperations.setSound(base, "m", shaped)).audioClips.single()
        assertEquals(0.5f, a.volume, 0f); assertEquals(1_000L, a.fadeInMs); assertEquals(2_000L, a.fadeOutMs)
    }
    @Test fun an_unknown_clip_is_refused() { assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.setSound(base, "zz", shaped))) }
    @Test fun timing_does_not_change() { assertEquals(20_000L, ok(TimelineOperations.setSound(base, "a", shaped)).videoDurationMs) }
    @Test fun the_fade_in_stays_with_the_first_half_and_the_fade_out_with_the_second() {
        val t = ok(TimelineOperations.split(ok(TimelineOperations.setSound(base, "b", shaped)), "b", 14_000, "b2")).videoClips
        assertEquals(listOf(1_000L, 0L), t.drop(1).map { it.fadeInMs }); assertEquals(listOf(0L, 2_000L), t.drop(1).map { it.fadeOutMs })
        assertEquals(listOf(0.5f, 0.5f), t.drop(1).map { it.volume })
    }
    @Test fun audio_clip_halves_share_the_fades_the_same_way() {
        val s = ok(TimelineOperations.split(ok(TimelineOperations.setSound(base, "m", shaped)), "m", 2_000, "m2")).audioClips.sortedBy { it.startTimeMs }
        assertEquals(listOf(1_000L, 0L), s.map { it.fadeInMs }); assertEquals(listOf(0L, 2_000L), s.map { it.fadeOutMs })
    }
    @Test fun the_command_undoes() {
        val c = SetSoundCommand("a", shaped)
        val after = (c.execute(base) as EditResult.Success).timeline
        assertEquals(0.5f, after.videoClips[0].volume, 0f)
        assertEquals(1f, c.undo(after).videoClips[0].volume, 0f)
    }
    @Test fun the_editor_undoes_and_redoes() {
        val e = DefaultTimelineEditor(base)
        e.setClipSound("a", shaped); e.undo(); assertEquals(1f, e.state.value.timeline.videoClips[0].volume, 0f)
        e.redo(); assertEquals(2_000L, e.state.value.timeline.videoClips[0].fadeOutMs)
    }
    @Test fun a_clip_can_never_hold_an_out_of_range_volume() {
        var failed = false
        try { TL.video("a", 1_000).copy(volume = 3f) } catch (e: IllegalArgumentException) { failed = true }
        assertEquals(true, failed)
    }
}
