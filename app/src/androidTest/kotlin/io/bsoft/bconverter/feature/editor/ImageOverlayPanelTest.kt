package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImageOverlayPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val picture = TextOverlay("p", "", 0, 3000, image = OverlayImage("/x/a.png", 400, 200))

    private fun show(actions: TextOverlayActions = TextOverlayActions(), done: () -> Unit = {}) {
        rule.setContent { ImageOverlayPanel(picture, actions, done) }
    }

    @Test fun it_shows_picture_settings_and_no_text_settings() {
        show()
        rule.onNodeWithText(s(R.string.image_dialog_title)).assertExists()
        rule.onNodeWithText(s(R.string.image_hint_gestures)).assertExists()
        rule.onNodeWithText(s(R.string.text_opacity)).performScrollTo().assertExists()
        rule.onNodeWithText(s(R.string.text_section_font)).assertDoesNotExist()
    }
    @Test fun delete_duplicate_and_done_report_the_overlay() {
        val calls = mutableListOf<String>()
        show(TextOverlayActions(onDelete = { calls += "delete $it" }, onDuplicate = { calls += "duplicate $it" }), done = { calls += "done" })
        rule.onNodeWithText(s(R.string.text_duplicate)).performClick()
        rule.onNodeWithText(s(R.string.text_delete)).performClick()
        assertEquals(listOf("duplicate p", "delete p", "done"), calls)
    }
    @Test fun the_layer_buttons_move_it_in_the_shared_order() {
        val moves = mutableListOf<EditorViewModel.OverlayLayerMove>()
        show(TextOverlayActions(onLayer = { _, m -> moves += m }))
        rule.onNodeWithText(s(R.string.text_layer_front)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.text_layer_back)).performScrollTo().performClick()
        assertEquals(listOf(EditorViewModel.OverlayLayerMove.FRONT, EditorViewModel.OverlayLayerMove.BACK), moves)
    }
}
