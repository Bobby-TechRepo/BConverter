package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropEditing
import io.bsoft.bconverter.domain.transform.KenBurns
import io.bsoft.bconverter.domain.transform.KenBurnsMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClipTransformPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val source = Resolution(1920, 1080)

    private fun show(t: ClipTransform, set: MutableList<ClipTransform> = mutableListOf(), all: MutableList<ClipTransform> = mutableListOf(), done: () -> Unit = {}) {
        rule.setContent { ClipTransformPanel(t, source, ClipTransformActions({ set += it }, { all += it }), done) }
    }

    @Test fun rotating_right_turns_a_quarter() {
        val set = mutableListOf<ClipTransform>(); show(ClipTransform.None, set)
        rule.onNodeWithText(s(R.string.crop_rotate_right)).performScrollTo().performClick()
        assertEquals(listOf(90), set.map { it.rotationDegrees })
    }
    @Test fun rotating_left_turns_the_other_way() {
        val set = mutableListOf<ClipTransform>(); show(ClipTransform.None, set)
        rule.onNodeWithText(s(R.string.crop_rotate_left)).performScrollTo().performClick()
        assertEquals(listOf(270), set.map { it.rotationDegrees })
    }
    @Test fun the_mirror_chips_toggle() {
        val set = mutableListOf<ClipTransform>(); show(ClipTransform(flipHorizontal = true), set)
        rule.onNodeWithText(s(R.string.crop_flip_horizontal)).assertIsSelected()
        rule.onNodeWithText(s(R.string.crop_flip_horizontal)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.crop_flip_vertical)).performScrollTo().performClick()
        assertEquals(false, set[0].flipHorizontal)
        assertEquals(true, set[1].flipVertical)
    }
    @Test fun picking_a_shape_crops_to_it() {
        val set = mutableListOf<ClipTransform>(); show(ClipTransform.None, set)
        rule.onNodeWithText(s(R.string.crop_shape_1_1)).performScrollTo().performClick()
        assertEquals(1080 to 1080, set.single().outputSize(1920, 1080))
    }
    @Test fun the_current_shape_is_marked() {
        show(ClipTransform(crop = CropEditing.cropFor(CropEditing.Shape.SQUARE, ClipTransform.None, 1920, 1080)))
        rule.onNodeWithText(s(R.string.crop_shape_1_1)).assertIsSelected()
    }
    @Test fun the_whole_picture_is_marked_when_nothing_is_cropped() {
        show(ClipTransform.None)
        rule.onNodeWithText(s(R.string.crop_shape_whole)).assertIsSelected()
    }
    @Test fun reset_is_only_offered_after_a_change() {
        show(ClipTransform.None); rule.onNodeWithText(s(R.string.crop_reset)).assertIsNotEnabled()
    }
    @Test fun reset_applies_the_identity() {
        val set = mutableListOf<ClipTransform>(); show(ClipTransform(rotationDegrees = 90), set)
        rule.onNodeWithText(s(R.string.crop_reset)).assertIsEnabled().performClick()
        assertEquals(listOf(ClipTransform.None), set)
    }
    @Test fun apply_to_all_hands_over_the_current_transform() {
        val all = mutableListOf<ClipTransform>(); val t = ClipTransform(rotationDegrees = 180)
        show(t, all = all); rule.onNodeWithText(s(R.string.crop_apply_all)).performClick()
        assertEquals(listOf(t), all)
    }
    @Test fun done_closes_the_panel() {
        var closed = false; show(ClipTransform.None, done = { closed = true })
        rule.onNodeWithText(s(R.string.crop_done)).performClick()
        assertEquals(true, closed)
    }

    private fun showMotion(k: KenBurns, set: MutableList<KenBurns> = mutableListOf(), all: MutableList<KenBurns> = mutableListOf()) {
        rule.setContent { ClipTransformPanel(ClipTransform.None, source, ClipTransformActions(onKenBurns = { set += it }, onKenBurnsToAll = { all += it }), {}, kenBurns = k) }
    }

    /**
     * The chips sit in a sideways-scrolling row, so scrolling to one never scrolls the panel up or down, and a chip below the visible
     * part gets a click that lands outside the window. The motion clicks therefore use the chip's click action instead of a touch. The hint is a plain line just under the chips: scrolling to it brings them into view.
     */
    private fun scrollToMotion() { rule.onNodeWithText(s(R.string.motion_hint)).performScrollTo() }

    @Test fun picking_a_motion_reports_it() {
        val set = mutableListOf<KenBurns>(); showMotion(KenBurns.None, set)
        scrollToMotion()
        rule.onNodeWithText(s(R.string.motion_zoom_in)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(KenBurnsMode.ZOOM_IN), set.map { it.mode })
    }
    @Test fun the_current_motion_is_selected_and_can_be_applied_to_all() {
        val all = mutableListOf<KenBurns>(); showMotion(KenBurns(KenBurnsMode.PAN_LEFT, 1.6f), all = all)
        scrollToMotion()
        rule.onNodeWithText(s(R.string.motion_pan_left)).performScrollTo().assertIsSelected()
        rule.onNodeWithText(s(R.string.motion_apply_all)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(KenBurns(KenBurnsMode.PAN_LEFT, 1.6f)), all)
    }
    @Test fun without_a_motion_there_is_no_strength_or_apply_all() {
        showMotion(KenBurns.None)
        rule.onNodeWithText(s(R.string.motion_apply_all)).assertDoesNotExist()
    }
}
