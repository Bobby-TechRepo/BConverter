package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.overlay.OverlayKeyframe
import io.bsoft.bconverter.domain.overlay.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OverlayKeyframeSectionTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val overlay = TextOverlay("t", "Hi", 1_000, 4_000, centerX = 0.3f, centerY = 0.6f)

    @Test fun a_keyframe_is_added_at_the_playhead_starting_from_the_overlay_as_it_stands() {
        var keys: List<OverlayKeyframe> = emptyList()
        rule.setContent { OverlayKeyframeSection(overlay, 2_000) { keys = it } }
        rule.onNodeWithText(s(R.string.overlay_keyframes_hint)).assertExists()
        rule.onNodeWithText(s(R.string.pip_keyframe_add)).performClick()
        assertEquals(listOf(0L, 1_000L), keys.map { it.offsetMs }); assertEquals(0.3f, keys[0].x)
    }
    @Test fun keyframes_are_listed_with_their_sliders_and_can_be_deleted() {
        var keys: List<OverlayKeyframe>? = null
        val keyed = overlay.copy(keyframes = listOf(OverlayKeyframe(0, 0.3f, 0.6f), OverlayKeyframe(2_000, 0.8f, 0.6f, 1.5f, 0.5f)))
        rule.setContent { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { OverlayKeyframeSection(keyed, 1_500) { keys = it } } }
        rule.onNodeWithText(s(R.string.overlay_keyframe_zoom)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_keyframe_time, "2.0")).performClick()
        rule.onNodeWithText(s(R.string.pip_keyframe_delete)).performScrollTo().performClick()
        assertEquals(1, keys!!.size)
    }
    @Test fun the_step_to_the_next_keyframe_can_be_made_smooth_but_the_last_keyframe_has_no_next() {
        var keys: List<OverlayKeyframe>? = null
        val keyed = overlay.copy(keyframes = listOf(OverlayKeyframe(0, 0.3f, 0.6f), OverlayKeyframe(2_000, 0.8f, 0.6f)))
        rule.setContent { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { OverlayKeyframeSection(keyed, 1_500) { keys = it } } }
        rule.onNodeWithContentDescription(s(R.string.pip_keyframe_ease)).performScrollTo().performClick()
        assertEquals(true, keys!![0].eased)
        rule.onNodeWithText(context.getString(R.string.pip_keyframe_time, "2.0")).performClick()
        rule.onNodeWithContentDescription(s(R.string.pip_keyframe_ease)).assertDoesNotExist()
    }
    @Test fun a_keyframe_has_a_turn_slider_showing_its_degrees() {
        val keyed = overlay.copy(keyframes = listOf(OverlayKeyframe(0, 0.3f, 0.6f, rotationDeg = 90f), OverlayKeyframe(2_000, 0.8f, 0.6f)))
        rule.setContent { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { OverlayKeyframeSection(keyed, 1_500) {} } }
        rule.onNodeWithContentDescription(s(R.string.overlay_keyframe_turn)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.overlay_keyframe_turn_value, 90)).performScrollTo().assertExists()
    }
    @Test fun nothing_can_be_added_when_the_playhead_is_outside_the_overlay() {
        rule.setContent { OverlayKeyframeSection(overlay, 9_000) {} }
        rule.onNodeWithText(s(R.string.pip_keyframe_add)).assertIsNotEnabled()
    }
}
