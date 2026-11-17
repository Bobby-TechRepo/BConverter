package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropRect
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipTransformOperationsTest {
    private val base = TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000))
    private val turned = ClipTransform(CropRect(0.1f, 0f, 1f, 1f), 90, flipHorizontal = true)

    @Test fun it_is_set_on_one_clip_only() {
        assertEquals(listOf(ClipTransform.None, turned, ClipTransform.None), ok(TimelineOperations.setTransform(base, "b", turned)).videoClips.map { it.transform })
    }
    @Test fun timing_does_not_change() { assertEquals(30_000L, ok(TimelineOperations.setTransform(base, "b", turned)).videoDurationMs) }
    @Test fun audio_and_unknown_clips_are_refused() {
        val withAudio = TL.withAudio(base, listOf(TL.audio("m")))
        assertEquals(EditError.WrongClipType, err(TimelineOperations.setTransform(withAudio, "m", turned)))
        assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.setTransform(base, "zz", turned)))
    }
    @Test fun apply_to_all() {
        assertEquals(listOf(turned, turned, turned), ok(TimelineOperations.setTransformForAll(base, turned)).videoClips.map { it.transform })
    }
    @Test fun apply_to_all_without_clips_fails() { assertEquals(EditError.NothingSelected, err(TimelineOperations.setTransformForAll(Timeline(), turned))) }
    @Test fun both_halves_of_a_split_keep_it() {
        val t = ok(TimelineOperations.split(ok(TimelineOperations.setTransform(base, "b", turned)), "b", 14_000, "b2"))
        assertEquals(listOf(ClipTransform.None, turned, turned, ClipTransform.None), t.videoClips.map { it.transform })
    }
    @Test fun the_command_undoes() {
        val c = SetTransformCommand("b", turned)
        val after = (c.execute(base) as EditResult.Success).timeline
        assertEquals(turned, after.videoClips[1].transform)
        assertEquals(ClipTransform.None, c.undo(after).videoClips[1].transform)
    }
    @Test fun the_editor_undoes_and_redoes() {
        val e = DefaultTimelineEditor(base)
        e.setClipTransform("a", turned); e.setTransformForAll(ClipTransform(rotationDegrees = 180))
        e.undo(); assertEquals(listOf(turned, ClipTransform.None, ClipTransform.None), e.state.value.timeline.videoClips.map { it.transform })
        e.redo(); assertEquals(180, e.state.value.timeline.videoClips[2].transform.rotationDegrees)
    }
}
