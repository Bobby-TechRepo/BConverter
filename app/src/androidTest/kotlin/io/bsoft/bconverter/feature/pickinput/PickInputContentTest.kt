package io.bsoft.bconverter.feature.pickinput

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.ResolvedInput
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PickInputContentTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun show(state: PickInputState, onSelect: () -> Unit = {}) = rule.setContent {
        PickInputContent(state, R.string.home_convert_video, R.string.select_video, onSelect, onBack = {})
    }

    @Test fun idle_offers_to_select_a_video_and_reports_the_tap() {
        var tapped = false
        show(PickInputState.Idle) { tapped = true }
        rule.onNodeWithText(ctx.getString(R.string.select_video)).assertIsDisplayed().performClick()
        assertTrue(tapped)
    }

    @Test fun resolving_tells_the_user_the_file_is_being_prepared() {
        show(PickInputState.Resolving)
        rule.onNodeWithText(ctx.getString(R.string.preparing_file)).assertIsDisplayed()
    }

    @Test fun ready_shows_the_selected_file_name() {
        show(PickInputState.Ready(ResolvedInput(File("/tmp/holiday.mp4"), "holiday.mp4", 2048, true)))
        rule.onNodeWithText("holiday.mp4").assertIsDisplayed()
    }

    @Test fun error_shows_a_human_readable_message_and_allows_retry() {
        show(PickInputState.Error(InputError.NotFound))
        rule.onNodeWithText(ctx.getString(R.string.error_not_found)).assertIsDisplayed()
        rule.onNodeWithText(ctx.getString(R.string.select_video)).assertIsDisplayed()
    }

    @Test fun permission_error_message_is_distinct_from_not_found() {
        show(PickInputState.Error(InputError.PermissionDenied))
        rule.onNodeWithText(ctx.getString(R.string.error_permission_denied)).assertIsDisplayed()
    }
}
