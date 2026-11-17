package io.bsoft.bconverter.core

import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.core.ui.HeightClass
import io.bsoft.bconverter.core.ui.READABLE_MAX_WIDTH_DP
import io.bsoft.bconverter.core.ui.WidthClass
import io.bsoft.bconverter.core.ui.WindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {
    @Test fun width_classes_follow_the_material_breakpoints() {
        assertEquals(WidthClass.COMPACT, WindowLayout(599, 900).widthClass)
        assertEquals(WidthClass.MEDIUM, WindowLayout(600, 900).widthClass)
        assertEquals(WidthClass.MEDIUM, WindowLayout(839, 900).widthClass)
        assertEquals(WidthClass.EXPANDED, WindowLayout(840, 900).widthClass)
    }

    @Test fun height_classes_follow_the_material_breakpoints() {
        assertEquals(HeightClass.COMPACT, WindowLayout(800, 479).heightClass)
        assertEquals(HeightClass.MEDIUM, WindowLayout(800, 480).heightClass)
        assertEquals(HeightClass.EXPANDED, WindowLayout(800, 900).heightClass)
    }

    @Test fun two_panes_need_an_expanded_width_or_a_medium_one_in_landscape() {
        assertFalse(WindowLayout.Phone.twoPane)
        assertFalse(WindowLayout(700, 1000).twoPane)   // medium, upright
        assertTrue(WindowLayout(700, 600).twoPane)     // medium, on its side
        assertTrue(WindowLayout(900, 1200).twoPane)    // expanded, even upright
    }

    @Test fun content_grows_wider_only_with_two_panes() {
        assertEquals(READABLE_MAX_WIDTH_DP.dp, WindowLayout.Phone.contentMaxWidth)
        assertEquals(WindowLayout.WIDE_CONTENT_MAX_DP.dp, WindowLayout(1280, 800).contentMaxWidth)
    }
}
