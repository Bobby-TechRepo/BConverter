package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.canvas.CanvasAspect
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CanvasPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun every_shape_fit_and_background_choice_is_offered() {
        rule.setContent { CanvasPanel(CanvasSettings(), {}, {}) }
        listOf(R.string.canvas_shape_auto, R.string.canvas_wide, R.string.canvas_tall, R.string.canvas_square, R.string.canvas_portrait,
            R.string.canvas_fit_fit, R.string.canvas_fit_fill, R.string.canvas_blur, R.string.canvas_black, R.string.canvas_white)
            .forEach { rule.onNodeWithText(text(it)).assertExists() }
    }
    @Test fun choosing_a_shape_reports_the_new_settings() {
        var last: CanvasSettings? = null
        rule.setContent { CanvasPanel(CanvasSettings(), { last = it }, {}) }
        rule.onNodeWithText(text(R.string.canvas_tall)).performClick()
        assertEquals(CanvasSettings(aspect = CanvasAspect.TALL), last)
    }
    @Test fun choosing_fill_and_blur_keeps_the_other_choices() {
        var last = CanvasSettings(CanvasAspect.SQUARE)
        rule.setContent { CanvasPanel(last, { last = it }, {}) }
        rule.onNodeWithText(text(R.string.canvas_fit_fill)).performScrollTo().performClick()
        assertEquals(CanvasSettings(CanvasAspect.SQUARE, CanvasFit.FILL), last)
    }
    @Test fun a_colour_and_the_blur_can_be_chosen() {
        var last: CanvasSettings? = null
        rule.setContent { CanvasPanel(CanvasSettings(), { last = it }, {}) }
        rule.onNodeWithText(text(R.string.canvas_white)).performScrollTo().performClick()
        assertEquals(CanvasBackground.Color(0xFFFFFF), last?.background)
        rule.onNodeWithText(text(R.string.canvas_blur)).performScrollTo().performClick()
        assertEquals(CanvasBackground.Blur, last?.background)
    }
    @Test fun done_closes_the_panel() {
        var closed = false
        rule.setContent { CanvasPanel(CanvasSettings(), {}, { closed = true }) }
        rule.onNodeWithText(text(R.string.canvas_done)).performClick()
        assertEquals(true, closed)
    }
}
