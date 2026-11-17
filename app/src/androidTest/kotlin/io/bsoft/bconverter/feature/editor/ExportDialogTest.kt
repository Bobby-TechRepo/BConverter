package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.SharePreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExportDialogTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    private fun show(onSharePreset: ((SharePreset) -> Unit)?, picked: SharePreset? = null) {
        rule.setContent {
            ExportDialog(ExportPreset.BALANCED, ContainerFormat.MP4, listOf(ContainerFormat.MP4), {}, {}, {}, {}, onSharePreset = onSharePreset, sharePreset = picked)
        }
    }

    @Test fun a_made_for_chip_reports_its_preset() {
        val picked = mutableListOf<SharePreset>(); show(onSharePreset = { picked += it })
        rule.onNodeWithText(s(R.string.share_made_for)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.share_vertical)).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText(s(R.string.share_whatsapp_status)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(SharePreset.VERTICAL_SHORTS, SharePreset.WHATSAPP_STATUS), picked)
    }
    @Test fun without_a_handler_the_made_for_row_is_not_shown() {
        show(null)
        rule.onNodeWithText(s(R.string.share_made_for)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.editor_export_size)).assertIsDisplayed()
    }
    @Test fun the_preset_the_choices_amount_to_is_shown_as_picked() {
        show({}, picked = SharePreset.SQUARE)
        rule.onNodeWithText(s(R.string.share_square)).assertIsSelected()
        rule.onNodeWithText(s(R.string.share_youtube)).assertIsNotSelected()
    }
}
