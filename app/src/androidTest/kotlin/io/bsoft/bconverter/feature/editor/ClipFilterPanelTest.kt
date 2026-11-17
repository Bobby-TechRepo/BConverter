package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.FilterPresets
import io.bsoft.bconverter.domain.filter.ToneMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClipFilterPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    private fun show(filter: ClipFilter, set: MutableList<ClipFilter> = mutableListOf(), all: MutableList<ClipFilter> = mutableListOf(), done: () -> Unit = {}) {
        rule.setContent { ClipFilterPanel(filter, ClipFilterActions({ set += it }, { all += it }), done) }
    }

    @Test fun choosing_a_preset_applies_it() {
        val set = mutableListOf<ClipFilter>()
        show(ClipFilter.None, set)
        rule.onNodeWithText(s(R.string.filter_preset_noir)).performScrollTo().performClick()
        assertEquals(listOf(FilterPresets.byId("noir")!!.filter), set)
    }
    @Test fun the_current_preset_is_marked() {
        show(FilterPresets.byId("warm")!!.filter)
        rule.onNodeWithText(s(R.string.filter_preset_warm)).assertIsSelected()
    }
    @Test fun the_original_is_marked_for_an_untouched_clip() {
        show(ClipFilter.None)
        rule.onNodeWithText(s(R.string.filter_preset_original)).assertIsSelected()
    }
    @Test fun reset_applies_the_identity() {
        val set = mutableListOf<ClipFilter>()
        show(ClipFilter(contrast = 0.3f), set)
        rule.onNodeWithText(s(R.string.filter_reset)).performClick()
        assertEquals(listOf(ClipFilter.None), set)
    }
    @Test fun apply_to_all_hands_over_the_current_look() {
        val all = mutableListOf<ClipFilter>()
        val look = ClipFilter(saturation = 0.4f)
        show(look, all = all)
        rule.onNodeWithText(s(R.string.filter_apply_all)).performClick()
        assertEquals(listOf(look), all)
    }
    @Test fun the_effects_tab_offers_tone_and_invert() {
        val set = mutableListOf<ClipFilter>()
        show(ClipFilter.None, set)
        rule.onNodeWithText(s(R.string.filter_group_detail)).performScrollTo().performClick()
        // "Sepia" is also a preset name; the tone chip comes after the presets.
        rule.onAllNodesWithText(s(R.string.filter_tone_sepia)).onLast().performScrollTo().performClick()
        assertEquals(ToneMode.SEPIA, set.single().tone)
    }
    @Test fun effects_the_preview_cannot_show_are_explained() {
        show(ClipFilter(blur = 0.5f))
        rule.onNodeWithText(s(R.string.filter_preview_note)).assertExists()
    }
    @Test fun done_closes_the_panel() {
        var closed = false
        show(ClipFilter.None, done = { closed = true })
        rule.onNodeWithText(s(R.string.filter_done)).performClick()
        assertEquals(true, closed)
    }
}
