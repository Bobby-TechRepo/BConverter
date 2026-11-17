package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class PipPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clip = PipClip("p", MediaSource.FileSource(File("/x.mp4")), 5_000, 0, 4_000, 1_000, PipLayout(0.5f, 0.5f, 0.4f, 1f), 1f)

    @Test fun the_controls_and_hints_are_shown() {
        rule.setContent { PipPanel(clip, PipActions()) {} }
        rule.onNodeWithText(context.getString(R.string.pip_size)).assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_opacity)).assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_hint)).assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_size).let { context.getString(R.string.pip_percent, 40) }).assertExists()
    }
    @Test fun the_border_fade_and_volume_controls_are_shown() {
        rule.setContent { PipPanel(clip, PipActions()) {} }
        listOf(R.string.pip_border, R.string.pip_fade_in, R.string.pip_fade_out, R.string.pip_volume).forEach { rule.onNodeWithText(context.getString(it)).performScrollTo().assertExists() }
    }
    @Test fun the_border_colour_choice_appears_only_with_a_border() {
        rule.setContent { PipPanel(clip.copy(layout = clip.layout.copy(borderWidth = 0.03f)), PipActions()) {} }
        rule.onNodeWithText(context.getString(R.string.pip_border_color)).performScrollTo().assertExists()
    }
    @Test fun the_colour_removal_switch_turns_a_key_on_and_off() {
        var key: io.bsoft.bconverter.domain.timeline.ChromaKey? = null
        rule.setContent { PipPanel(clip.copy(chromaKey = key), PipActions(onChromaKey = { key = it })) {} }
        rule.onNodeWithText(context.getString(R.string.pip_key)).performScrollTo()
        rule.onNodeWithContentDescription(context.getString(R.string.pip_key)).performClick()
        assertEquals(io.bsoft.bconverter.domain.timeline.ChromaKey(), key)
    }
    @Test fun the_colour_choices_appear_only_with_a_key() {
        rule.setContent { PipPanel(clip.copy(chromaKey = io.bsoft.bconverter.domain.timeline.ChromaKey()), PipActions()) {} }
        rule.onNodeWithText(context.getString(R.string.pip_key_green)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_key_range)).performScrollTo().assertExists()
    }
    @Test fun the_glide_switch_turns_a_motion_on_and_the_end_controls_follow() {
        var motion: io.bsoft.bconverter.domain.timeline.PipMotion? = null
        rule.setContent { PipPanel(clip.copy(motion = motion), PipActions(onMotion = { motion = it })) {} }
        rule.onNodeWithText(context.getString(R.string.pip_glide)).performScrollTo()
        rule.onNodeWithText(context.getString(R.string.pip_glide_x)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.pip_glide)).performClick()
        assertEquals(true, motion != null)
    }
    @Test fun the_end_controls_are_shown_when_there_is_a_motion() {
        rule.setContent { PipPanel(clip.copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.8f, 0.5f)), PipActions()) {} }
        listOf(R.string.pip_glide_x, R.string.pip_glide_y, R.string.pip_glide_size, R.string.pip_glide_ease).forEach { rule.onNodeWithText(context.getString(it)).performScrollTo().assertExists() }
    }
    @Test fun a_keyframe_is_added_at_the_playhead_with_the_clips_present_look() {
        var keys: List<io.bsoft.bconverter.domain.timeline.PipKeyframe> = emptyList()
        rule.setContent { PipPanel(clip, PipActions(onKeyframes = { keys = it }), playheadOffsetMs = 1_000) {} }
        rule.onNodeWithText(context.getString(R.string.pip_keyframes_hint)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_keyframe_add)).performScrollTo().performClick()
        assertEquals(listOf(0L, 1_000L), keys.map { it.offsetMs })
    }
    @Test fun keyframes_are_listed_and_replace_the_plain_position_controls() {
        val keyed = clip.copy(keyframes = listOf(io.bsoft.bconverter.domain.timeline.PipKeyframe(0, 0.5f, 0.5f, 0.4f, 1f), io.bsoft.bconverter.domain.timeline.PipKeyframe(2_000, 0.8f, 0.5f, 0.4f, 1f)))
        rule.setContent { PipPanel(keyed, PipActions(), playheadOffsetMs = 500) {} }
        rule.onNodeWithText(context.getString(R.string.pip_keyframe_time, "2.0")).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_keyframe_delete)).performScrollTo().assertExists()
        rule.onNodeWithText(context.getString(R.string.pip_glide)).assertDoesNotExist()
    }
    @Test fun a_keyframe_cannot_be_added_when_the_playhead_is_outside_the_clip() {
        rule.setContent { PipPanel(clip, PipActions(), playheadOffsetMs = -500) {} }
        rule.onNodeWithText(context.getString(R.string.pip_keyframe_add)).performScrollTo().assertIsNotEnabled()
    }
    @Test fun a_shape_can_be_chosen_and_the_border_goes_with_a_shape() {
        var shape: io.bsoft.bconverter.domain.timeline.PipShape? = null
        rule.setContent { PipPanel(clip, PipActions(onShape = { shape = it })) {} }
        rule.onNodeWithText(context.getString(R.string.pip_shape_circle)).performScrollTo().performClick()
        assertEquals(io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE, shape)
    }
    @Test fun a_shaped_picture_offers_no_border() {
        rule.setContent { PipPanel(clip.copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.ROUNDED), PipActions()) {} }
        rule.onNodeWithText(context.getString(R.string.pip_border)).assertDoesNotExist()
    }
    @Test fun a_speed_can_be_chosen() {
        var speed = 0f
        rule.setContent { PipPanel(clip, PipActions(onSpeed = { speed = it })) {} }
        rule.onNodeWithText(context.getString(R.string.pip_speed_value, "2")).performScrollTo().performClick()
        assertEquals(2f, speed)
    }
    @Test fun bring_to_front_is_reported() {
        var front = 0
        rule.setContent { PipPanel(clip, PipActions(onFront = { front++ })) {} }
        rule.onNodeWithText(context.getString(R.string.pip_front)).performScrollTo().performClick()
        assertEquals(1, front)
    }
    @Test fun done_closes_the_panel() {
        var closed = false
        rule.setContent { PipPanel(clip, PipActions()) { closed = true } }
        rule.onNodeWithText(context.getString(R.string.pip_done)).performClick()
        assertEquals(true, closed)
    }
}
