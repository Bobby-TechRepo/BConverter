package io.bsoft.bconverter.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineWindowTest {
    @Test fun a_block_inside_the_window_is_unchanged() =
        assertEquals(VisibleBlock(ClipBlock("a", 100f, 50f), 0f), TimelineWindow.clip(ClipBlock("a", 100f, 50f), 0f, 1000f))

    @Test fun a_block_outside_the_window_is_not_laid_out() {
        assertNull(TimelineWindow.clip(ClipBlock("a", 2000f, 50f), 0f, 1000f))
        assertNull(TimelineWindow.clip(ClipBlock("a", -500f, 100f), 0f, 1000f))
        assertNull(TimelineWindow.clip(ClipBlock("a", 1000f, 100f), 0f, 1000f)) // touching the edge only
    }

    @Test fun a_huge_block_is_cut_to_the_window_and_remembers_the_cut() {
        // A 30-minute clip at the maximum zoom (600 dp/s) is over a million dp wide; only the window is laid out.
        val huge = ClipBlock("a", 0f, 30 * 60 * 600f)
        val v = TimelineWindow.clip(huge, 500_000f, 503_000f)!!
        assertEquals(500_000f, v.block.xPx); assertEquals(3_000f, v.block.widthPx); assertEquals(500_000f, v.cutDp)
    }

    @Test fun a_block_starting_before_the_window_is_cut_at_its_left() {
        val v = TimelineWindow.clip(ClipBlock("a", -300f, 500f), 0f, 1000f)!!
        assertEquals(ClipBlock("a", 0f, 200f), v.block); assertEquals(300f, v.cutDp)
    }

    @Test fun the_window_start_moves_in_steps() {
        assertEquals(-200f, TimelineWindow.windowStartDp(0f, 2f, 150f))
        assertEquals(200f, TimelineWindow.windowStartDp(2f * (150f + 299f), 2f, 150f))
    }

    @Test fun the_scroll_range_never_goes_negative() {
        assertEquals(0f, TimelineWindow.maxScrollPx(100f, 1000f, 2f))
        assertEquals(1000f, TimelineWindow.maxScrollPx(1000f, 1000f, 2f))
    }
}
