package io.bsoft.bconverter.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.settings.AppSettings
import io.bsoft.bconverter.domain.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg a: Any) = context.getString(id, *a)

    private fun show(
        state: SettingsUiState = SettingsUiState(),
        dynamic: Boolean = true,
        onTheme: (ThemeMode) -> Unit = {}, onDynamic: (Boolean) -> Unit = {}, onClear: () -> Unit = {},
    ) = rule.setContent { SettingsContent(state, dynamic, {}, onTheme, onDynamic, onClear) }

    @Test fun the_fonts_entry_opens_the_fonts_screen() {
        var opened = false
        rule.setContent { SettingsContent(SettingsUiState(), true, {}, {}, {}, {}, onOpenFonts = { opened = true }) }
        rule.onNodeWithText(str(R.string.settings_fonts_hint)).performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun the_current_theme_is_the_selected_one() {
        show(SettingsUiState(settings = AppSettings(ThemeMode.DARK)))
        rule.onNodeWithText(str(R.string.settings_theme_dark)).assertIsSelected()
    }

    @Test fun choosing_a_theme_reports_it() {
        var chosen: ThemeMode? = null
        show(onTheme = { chosen = it })
        rule.onNodeWithText(str(R.string.settings_theme_light)).performClick()
        assertEquals(ThemeMode.LIGHT, chosen)
    }

    @Test fun dynamic_colour_is_offered_only_where_the_device_supports_it() {
        show(dynamic = false)
        assertEquals(0, rule.onAllNodesWithText(str(R.string.settings_dynamic_color)).fetchSemanticsNodes().size)
    }

    @Test fun the_dynamic_colour_row_toggles() {
        var value: Boolean? = null
        show(SettingsUiState(settings = AppSettings(dynamicColor = true)), onDynamic = { value = it })
        rule.onNodeWithText(str(R.string.settings_dynamic_color)).performClick()
        assertEquals(false, value)
    }

    @Test fun the_cache_size_is_shown_and_clearing_is_offered() {
        var cleared = false
        show(SettingsUiState(cacheBytes = 1536), onClear = { cleared = true })
        rule.onNodeWithText(str(R.string.settings_cache_size, "1.5 KB")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_clear_cache)).performScrollTo().assertIsEnabled().performClick()
        assertTrue(cleared)
    }

    @Test fun there_is_nothing_to_clear_when_the_cache_is_empty_or_busy() {
        show(SettingsUiState(cacheBytes = 0)); rule.onNodeWithText(str(R.string.settings_clear_cache)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun clearing_is_disabled_while_it_runs() {
        show(SettingsUiState(cacheBytes = 100, clearing = true)); rule.onNodeWithText(str(R.string.settings_clear_cache)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun the_result_of_clearing_is_reported() {
        show(SettingsUiState(freedBytes = 2048)); rule.onNodeWithText(str(R.string.settings_cache_cleared, "2.0 KB")).performScrollTo().assertIsDisplayed()
    }

    @Test fun a_failed_clear_says_so() {
        show(SettingsUiState(clearFailed = true)); rule.onNodeWithText(str(R.string.settings_cache_clear_failed)).performScrollTo().assertIsDisplayed()
    }
}
