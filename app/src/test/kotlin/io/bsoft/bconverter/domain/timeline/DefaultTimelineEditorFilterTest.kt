package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTimelineEditorFilterTest {
    private val warm = ClipFilter(temperature = 0.4f)
    private fun editor() = DefaultTimelineEditor(TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000)))
    private fun DefaultTimelineEditor.filters() = state.value.timeline.videoClips.map { it.filter }

    @Test fun set_undo_redo() {
        val e = editor()
        assertTrue(e.setClipFilter("a", warm) is EditResult.Success)
        assertEquals(listOf(warm, ClipFilter.None), e.filters())
        e.undo(); assertEquals(listOf(ClipFilter.None, ClipFilter.None), e.filters())
        e.redo(); assertEquals(listOf(warm, ClipFilter.None), e.filters())
    }
    @Test fun apply_to_all_is_one_undo_step() {
        val e = editor()
        e.setFilterForAll(warm); assertEquals(listOf(warm, warm), e.filters())
        e.undo(); assertEquals(listOf(ClipFilter.None, ClipFilter.None), e.filters())
    }
}
