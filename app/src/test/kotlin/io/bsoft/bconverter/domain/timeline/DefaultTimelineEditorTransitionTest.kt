package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTimelineEditorTransitionTest {
    private fun editor() = DefaultTimelineEditor(TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000)), SequentialIds())

    @Test fun setting_a_transition_is_an_undoable_edit() {
        val e = editor()
        assertTrue(e.setTransition("b", TransitionType.CROSSFADE, 1_000) is EditResult.Success)
        assertEquals(19_000L, e.state.value.timeline.videoDurationMs)
        e.undo(); assertEquals(20_000L, e.state.value.timeline.videoDurationMs)
        e.redo(); assertEquals(19_000L, e.state.value.timeline.videoDurationMs)
    }

    @Test fun a_length_outside_the_allowed_range_is_refused() {
        val e = editor()
        assertEquals(EditError.InvalidTransition, err(e.setTransition("b", TransitionType.FADE_BLACK, 100)))
        assertEquals(EditError.InvalidTransition, err(e.setTransition("b", TransitionType.FADE_BLACK, 5_000)))
        assertEquals(20_000L, e.state.value.timeline.videoDurationMs)
    }

    @Test fun choosing_none_ignores_the_length() {
        val e = editor(); e.setTransition("b", TransitionType.CROSSFADE, 1_000)
        assertTrue(e.setTransition("b", TransitionType.NONE, 0) is EditResult.Success)
        assertEquals(20_000L, e.state.value.timeline.videoDurationMs)
    }

    @Test fun a_crossfade_that_shortens_the_timeline_pulls_the_playhead_in_with_it() {
        val e = editor(); e.seekTo(20_000 + SeekRange.BLANK_TAIL_MS)
        e.setTransition("b", TransitionType.CROSSFADE, 2_000)
        assertEquals(18_000L + SeekRange.BLANK_TAIL_MS, e.state.value.playheadMs)
    }
}
