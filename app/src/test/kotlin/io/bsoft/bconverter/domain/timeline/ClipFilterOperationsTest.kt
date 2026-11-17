package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipFilterOperationsTest {
    private val base = TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000))
    private val warm = ClipFilter(temperature = 0.4f)

    @Test fun a_filter_is_set_on_one_clip_only() {
        val t = ok(TimelineOperations.setFilter(base, "b", warm))
        assertEquals(listOf(ClipFilter.None, warm, ClipFilter.None), t.videoClips.map { it.filter })
    }
    @Test fun setting_a_filter_does_not_change_timing() {
        assertEquals(30_000L, ok(TimelineOperations.setFilter(base, "b", warm)).videoDurationMs)
    }
    @Test fun the_filter_can_be_cleared() {
        val t = ok(TimelineOperations.setFilter(ok(TimelineOperations.setFilter(base, "b", warm)), "b", ClipFilter.None))
        assertEquals(ClipFilter.None, t.videoClips[1].filter)
    }
    @Test fun audio_and_unknown_clips_have_no_filter() {
        val withAudio = TL.withAudio(base, listOf(TL.audio("m")))
        assertEquals(EditError.WrongClipType, err(TimelineOperations.setFilter(withAudio, "m", warm)))
        assertEquals(EditError.ClipNotFound("zz"), err(TimelineOperations.setFilter(base, "zz", warm)))
    }
    @Test fun apply_to_all_copies_the_look_to_every_video_clip() {
        val t = ok(TimelineOperations.setFilterForAll(base, warm))
        assertEquals(listOf(warm, warm, warm), t.videoClips.map { it.filter })
    }
    @Test fun apply_to_all_without_clips_fails() {
        assertEquals(EditError.NothingSelected, err(TimelineOperations.setFilterForAll(Timeline(), warm)))
    }
    @Test fun both_halves_of_a_split_keep_the_filter() {
        val t = ok(TimelineOperations.split(ok(TimelineOperations.setFilter(base, "b", warm)), "b", 14_000, "b2"))
        assertEquals(listOf(ClipFilter.None, warm, warm, ClipFilter.None), t.videoClips.map { it.filter })
    }
    @Test fun the_command_undoes_to_the_previous_look() {
        val c = SetFilterCommand("b", warm)
        val after = (c.execute(base) as EditResult.Success).timeline
        assertEquals(warm, after.videoClips[1].filter)
        assertEquals(ClipFilter.None, c.undo(after).videoClips[1].filter)
    }
    @Test fun the_apply_all_command_undoes_in_one_step() {
        val c = SetFilterForAllCommand(warm)
        val after = (c.execute(base) as EditResult.Success).timeline
        assertEquals(base.videoClips.map { it.filter }, c.undo(after).videoClips.map { it.filter })
    }
}
