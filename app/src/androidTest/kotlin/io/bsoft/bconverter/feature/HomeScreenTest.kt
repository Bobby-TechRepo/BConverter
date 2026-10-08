package io.bsoft.bconverter.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun title(d: HomeDestination) = context.getString(d.titleRes)

    @Test fun shows_all_six_entries() {
        rule.setContent { HomeScreen(HomeUiState(HomeDestination.entries), onDestinationClick = {}) }
        HomeDestination.entries.forEach { rule.onNodeWithText(title(it)).assertIsDisplayed() }
    }

    @Test fun tapping_convert_video_reports_that_destination() {
        var clicked: HomeDestination? = null
        rule.setContent { HomeScreen(HomeUiState(HomeDestination.entries), onDestinationClick = { clicked = it }) }
        rule.onNodeWithText(title(HomeDestination.CONVERT_VIDEO)).performClick()
        assertEquals(HomeDestination.CONVERT_VIDEO, clicked)
    }
}
