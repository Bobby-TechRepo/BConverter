package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.transform.KenBurns
import io.bsoft.bconverter.domain.transform.KenBurnsMode
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class KenBurnsOperationsTest {
    private val base = TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000))
    private val zoom = KenBurns(KenBurnsMode.ZOOM_IN, 1.4f)

    @Test fun it_is_set_on_one_clip_only() {
        assertEquals(listOf(KenBurns.None, zoom, KenBurns.None), ok(TimelineOperations.setKenBurns(base, "b", zoom)).videoClips.map { it.kenBurns })
    }
    @Test fun timing_does_not_change() = assertEquals(30_000L, ok(TimelineOperations.setKenBurns(base, "b", zoom)).videoDurationMs)
    @Test fun audio_and_unknown_clips_are_refused() {
        val withAudio = TL.withAudio(base, listOf(TL.audio("m")))
        assertEquals(EditError.WrongClipType, err(TimelineOperations.setKenBurns(withAudio, "m", zoom)))
        assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.setKenBurns(base, "zz", zoom)))
    }
    @Test fun a_freeze_frame_has_no_motion_to_give() {
        val t = ok(TimelineOperations.insertFreeze(base, "b", 14_000, "f", "b2", 2_000))
        assertEquals(EditError.NotForFreezeFrame, err(TimelineOperations.setKenBurns(t, "f", zoom)))
    }
    @Test fun apply_to_all_skips_freeze_frames() {
        val t = ok(TimelineOperations.insertFreeze(base, "b", 14_000, "f", "b2", 2_000))
        val all = ok(TimelineOperations.setKenBurnsForAll(t, zoom)).videoClips
        assertEquals(all.map { it.id }.zip(all.map { it.kenBurns }).filter { it.first == "f" }.map { it.second }, listOf(KenBurns.None))
        assertEquals(true, all.filter { !it.isFreeze }.all { it.kenBurns == zoom })
    }
    @Test fun apply_to_all_without_clips_fails() = assertEquals(EditError.NothingSelected, err(TimelineOperations.setKenBurnsForAll(Timeline(), zoom)))
    @Test fun both_halves_of_a_split_keep_it() {
        val t = ok(TimelineOperations.split(ok(TimelineOperations.setKenBurns(base, "b", zoom)), "b", 14_000, "b2"))
        assertEquals(listOf(KenBurns.None, zoom, zoom, KenBurns.None), t.videoClips.map { it.kenBurns })
    }
    @Test fun the_editor_undoes_and_redoes() {
        val e = DefaultTimelineEditor(base)
        e.setKenBurns("a", zoom); e.setKenBurnsForAll(KenBurns(KenBurnsMode.PAN_UP))
        e.undo(); assertEquals(listOf(zoom, KenBurns.None, KenBurns.None), e.state.value.timeline.videoClips.map { it.kenBurns })
        e.redo(); assertEquals(KenBurnsMode.PAN_UP, e.state.value.timeline.videoClips[2].kenBurns.mode)
    }
}
