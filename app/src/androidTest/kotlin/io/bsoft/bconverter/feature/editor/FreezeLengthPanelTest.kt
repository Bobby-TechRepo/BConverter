package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FreezeLengthPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun the_current_length_and_the_preview_note_are_shown() {
        rule.setContent { FreezeLengthPanel(2_500, {}, {}) }
        rule.onNodeWithText(context.getString(R.string.freeze_length_value, 2.5f)).assertExists()
        rule.onNodeWithText(context.getString(R.string.freeze_preview_note)).assertExists()
    }
    @Test fun done_closes_the_panel() {
        var closed = false
        rule.setContent { FreezeLengthPanel(2_000, {}, { closed = true }) }
        rule.onNodeWithText(context.getString(R.string.freeze_done)).performClick()
        assertEquals(true, closed)
    }
}
