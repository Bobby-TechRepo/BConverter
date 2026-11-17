package io.bsoft.bconverter.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.CompositionLocalProvider
import io.bsoft.bconverter.core.ui.LocalWindowLayout
import io.bsoft.bconverter.core.ui.WindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun title(d: HomeDestination) = context.getString(d.titleRes)

    private val tiles = HomeViewModel().uiState.value

    @Test fun shows_all_six_tiles() {
        rule.setContent { HomeScreen(tiles, onDestinationClick = {}) }
        assertEquals(6, tiles.destinations.size)
        // Home scrolls on short screens, so each entry is scrolled into view before it is checked.
        tiles.destinations.forEach { rule.onNodeWithText(title(it)).performScrollTo().assertIsDisplayed() }
    }

    @Test fun settings_is_a_top_bar_action_and_fonts_is_not_on_home() {
        var clicked: HomeDestination? = null
        rule.setContent { HomeScreen(tiles, onDestinationClick = { clicked = it }) }
        rule.onNodeWithText(title(HomeDestination.FONTS)).assertDoesNotExist()
        rule.onNodeWithContentDescription(title(HomeDestination.SETTINGS)).assertIsDisplayed().performClick()
        assertEquals(HomeDestination.SETTINGS, clicked)
    }

    @Test fun tapping_convert_video_reports_that_destination() {
        var clicked: HomeDestination? = null
        rule.setContent { HomeScreen(tiles, onDestinationClick = { clicked = it }) }
        rule.onNodeWithText(title(HomeDestination.CONVERT_VIDEO)).performScrollTo().performClick()
        assertEquals(HomeDestination.CONVERT_VIDEO, clicked)
    }

    @Test fun exports_is_a_top_bar_action_not_a_tile() {
        var clicked: HomeDestination? = null
        rule.setContent { HomeScreen(tiles, onDestinationClick = { clicked = it }) }
        rule.onNodeWithContentDescription(title(HomeDestination.JOBS)).assertIsDisplayed().performClick()
        assertEquals(HomeDestination.JOBS, clicked)
    }

    @Test fun a_wide_window_shows_new_work_and_recent_work_side_by_side() {
        rule.setContent {
            CompositionLocalProvider(LocalWindowLayout provides WindowLayout(1280, 800)) { HomeScreen(tiles, onDestinationClick = {}) }
        }
        val hero = rule.onNodeWithText(title(HomeDestination.VIDEO_EDITOR)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val recent = rule.onNodeWithText(title(HomeDestination.RECENT_PROJECTS)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("recent work sits to the right of the editor", recent.left > hero.right)
    }
}
