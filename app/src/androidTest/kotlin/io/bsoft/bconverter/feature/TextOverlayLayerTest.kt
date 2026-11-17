package io.bsoft.bconverter.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.feature.editor.NoOverlayFonts
import io.bsoft.bconverter.feature.editor.OverlayGuide
import io.bsoft.bconverter.feature.editor.TextOverlayActions
import io.bsoft.bconverter.feature.editor.TextOverlayLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TextOverlayLayerTest {
    @get:Rule val rule = createComposeRule()
    private val overlay = TextOverlay("a", "Hello", 1_000, 3_000)

    private fun show(playheadMs: Long, selected: String? = null, actions: TextOverlayActions = TextOverlayActions()) = rule.setContent {
        Box(Modifier.size(400.dp, 225.dp)) {
            TextOverlayLayer(listOf(overlay), selected, playheadMs, 16f / 9f, OverlayGuide(), NoOverlayFonts, actions, onEdit = {}, modifier = Modifier.size(400.dp, 225.dp))
        }
    }

    @Test fun what_lies_outside_the_output_frame_is_not_drawn() {
        // A big white text centred on the right edge of a 400 dp frame; the 200 dp to the right of the frame must stay black.
        val spill = TextOverlay("s", "WWWWWWWW", 0, 10_000, centerX = 1.0f, style = io.bsoft.bconverter.domain.overlay.TextOverlayStyle(sizeFraction = 0.3f, color = 0xFFFFFFFF.toInt()))
        rule.setContent {
            Box(Modifier.size(600.dp, 225.dp).background(Color.Black)) {
                TextOverlayLayer(listOf(spill), null, 1_000, 16f / 9f, OverlayGuide(), NoOverlayFonts, TextOverlayActions(), onEdit = {}, modifier = Modifier.size(400.dp, 225.dp))
            }
        }
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().toPixelMap()
        val frameRightPx = (400 * androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density).toInt()
        var litOutside = 0; var litInside = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val c = image[x, y]; val lit = c.red > 0.5f && c.green > 0.5f && c.blue > 0.5f
            if (lit) { if (x >= frameRightPx + 2) litOutside++ else litInside++ }
        }
        assertTrue("the text should show inside the frame", litInside > 0)
        assertEquals("nothing may be drawn outside the frame", 0, litOutside)
    }

    @Test fun the_text_is_drawn_only_while_it_is_shown() {
        show(2_000); rule.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test fun it_is_not_drawn_before_it_starts_or_after_it_ends() {
        show(500); rule.onNodeWithText("Hello").assertDoesNotExist()
    }

    @Test fun it_is_not_drawn_after_it_ends() {
        show(3_000); rule.onNodeWithText("Hello").assertDoesNotExist()
    }

    @Test fun tapping_the_text_selects_it() {
        var selected: String? = "none"
        show(2_000, actions = TextOverlayActions(onSelect = { selected = it }))
        rule.onRoot().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertEquals("a", selected)
    }

    @Test fun tapping_selects_at_once_without_waiting_for_a_possible_double_tap() {
        var selected: String? = "none"
        show(2_000, actions = TextOverlayActions(onSelect = { selected = it }))
        rule.onRoot().performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals("a", selected)
    }

    @Test fun double_tapping_the_text_opens_the_editor() {
        var edits = 0
        rule.setContent {
            Box(Modifier.size(400.dp, 225.dp)) {
                TextOverlayLayer(listOf(overlay), null, 2_000, 16f / 9f, OverlayGuide(), NoOverlayFonts, TextOverlayActions(), onEdit = { edits++ }, modifier = Modifier.size(400.dp, 225.dp))
            }
        }
        rule.onRoot().performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertEquals(1, edits)
    }

    @Test fun a_drag_moves_by_everything_including_what_was_covered_before_the_touch_slop() {
        var dx = 0f
        show(2_000, "a", TextOverlayActions(onTransform = { x, _, _, _ -> dx += x }))
        rule.onRoot().performTouchInput { down(center); moveBy(Offset(10f, 0f)); moveBy(Offset(10f, 0f)); moveBy(Offset(60f, 0f)); up() }
        rule.waitForIdle()
        assertTrue("moved ($dx)", dx > 70f / 1000f)
    }

    @Test fun tapping_empty_space_clears_the_selection() {
        val selects = mutableListOf<String?>()
        show(2_000, "a", TextOverlayActions(onSelect = { selects += it }))
        rule.onRoot().performTouchInput { click(Offset(width * 0.1f, height * 0.1f)) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertEquals("onSelect calls", listOf<String?>(null), selects)
    }

    @Test fun tapping_empty_space_without_a_selection_also_reports_no_overlay() {
        val selects = mutableListOf<String?>()
        show(2_000, null, TextOverlayActions(onSelect = { selects += it }))
        rule.onRoot().performTouchInput { click(Offset(width * 0.1f, height * 0.1f)) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertEquals("onSelect calls", listOf<String?>(null), selects)
    }

    @Test fun tapping_the_text_while_it_is_selected_keeps_it_selected() {
        val selects = mutableListOf<String?>()
        show(2_000, "a", TextOverlayActions(onSelect = { selects += it }))
        rule.onRoot().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        assertEquals("onSelect calls", listOf<String?>("a"), selects)
    }

    @Test fun dragging_with_a_selection_moves_it_and_reports_the_end() {
        var dx = 0f; var ended = false
        show(2_000, "a", TextOverlayActions(onTransform = { x, _, _, _ -> dx += x }, onGestureEnd = { ended = true }))
        rule.onRoot().performTouchInput { down(center); moveBy(Offset(80f, 0f)); up() }
        rule.waitForIdle()
        assertTrue("moved right ($dx)", dx > 0f); assertTrue(ended)
    }

    @Test fun dragging_a_keyframed_overlay_edits_the_keyframe_at_the_playhead() {
        val keyed = overlay.copy(keyframes = listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.5f, 0.5f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(1_500, 0.5f, 0.5f)))
        var dx = 0f; var at = -1L; var plain = 0
        rule.setContent {
            Box(Modifier.size(400.dp, 225.dp)) {
                TextOverlayLayer(
                    listOf(keyed), "a", 1_750, 16f / 9f, OverlayGuide(), NoOverlayFonts,
                    TextOverlayActions(onTransform = { _, _, _, _ -> plain++ }, onKeyframeTransform = { _, offset, x, _, _, _ -> dx += x; at = offset }),
                    onEdit = {}, modifier = Modifier.size(400.dp, 225.dp),
                )
            }
        }
        rule.onRoot().performTouchInput { down(center); moveBy(Offset(80f, 0f)); up() }
        rule.waitForIdle()
        assertTrue("moved ($dx)", dx > 0f); assertEquals(750L, at); assertEquals(0, plain)
    }
    @Test fun dragging_without_a_selection_does_not_move_anything() {
        var calls = 0
        show(2_000, null, TextOverlayActions(onTransform = { _, _, _, _ -> calls++ }))
        rule.onRoot().performTouchInput { down(center); moveBy(Offset(80f, 0f)); up() }
        rule.waitForIdle()
        assertEquals(0, calls)
    }
}
