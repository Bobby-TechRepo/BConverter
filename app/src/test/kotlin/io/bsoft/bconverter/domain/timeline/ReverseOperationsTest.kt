package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class ReverseOperationsTest {
    private val base = TL.withAudio(TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000)), listOf(TL.audio("m")))

    @Test fun one_clip_is_reversed_and_forwarded_again() {
        val r = ok(TimelineOperations.setReversed(base, "b", true))
        assertEquals(listOf(false, true), r.videoClips.map { it.reversed })
        assertEquals(listOf(false, false), ok(TimelineOperations.setReversed(r, "b", false)).videoClips.map { it.reversed })
    }
    @Test fun timing_does_not_change() { assertEquals(20_000L, ok(TimelineOperations.setReversed(base, "a", true)).videoDurationMs) }
    @Test fun audio_clips_and_unknown_clips_are_refused() {
        assertEquals(EditError.WrongClipType, err(TimelineOperations.setReversed(base, "m", true)))
        assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.setReversed(base, "zz", true)))
    }
    @Test fun splitting_a_reversed_clip_puts_the_later_part_of_the_source_first() {
        val split = ok(TimelineOperations.split(ok(TimelineOperations.setReversed(base, "a", true)), "a", 3_000, "a2"))
        val t = split.videoClips
        assertEquals(listOf(7_000L to 10_000L, 0L to 7_000L), t.take(2).map { it.sourceStartMs to it.sourceEndMs })
        assertEquals(listOf(true, true), t.take(2).map { it.reversed })
        assertEquals(20_000L, split.videoDurationMs)
    }
    @Test fun a_forward_split_is_unchanged() {
        val t = ok(TimelineOperations.split(base, "a", 3_000, "a2")).videoClips
        assertEquals(listOf(0L to 3_000L, 3_000L to 10_000L), t.take(2).map { it.sourceStartMs to it.sourceEndMs })
    }
    @Test fun the_command_undoes() {
        val c = SetReversedCommand("a", true)
        val after = (c.execute(base) as EditResult.Success).timeline
        assertEquals(true, after.videoClips[0].reversed); assertEquals(false, c.undo(after).videoClips[0].reversed)
    }
    @Test fun the_editor_undoes_and_redoes() {
        val e = DefaultTimelineEditor(base)
        e.setClipReversed("a", true); e.undo(); assertEquals(false, e.state.value.timeline.videoClips[0].reversed)
        e.redo(); assertEquals(true, e.state.value.timeline.videoClips[0].reversed)
    }
}
