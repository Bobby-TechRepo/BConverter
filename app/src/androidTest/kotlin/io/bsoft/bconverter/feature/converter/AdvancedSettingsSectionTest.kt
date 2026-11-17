package io.bsoft.bconverter.feature.converter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.usecase.AdvancedChoices
import io.bsoft.bconverter.domain.usecase.AdvancedOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AdvancedSettingsSectionTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)

    private val choices = AdvancedChoices(
        videoEncoded = true, audioEncoded = true, crfRange = 0..51,
        resolutionHeights = listOf(720, 480), frameRates = listOf(30.0, 24.0),
        profiles = listOf("main", "high"), levels = listOf("4.1"), pixelFormats = listOf("yuv420p"),
        audioBitrates = listOf(128, 192), sampleRates = listOf(44100, 48000), channelCounts = listOf(1, 2),
    )

    /** Holds the options like the real screen does, so a change re-renders the section. */
    private fun show(c: AdvancedChoices = choices, initial: AdvancedOptions = AdvancedOptions(), onChange: (AdvancedOptions) -> Unit = {}) =
        rule.setContent {
            var options by androidx.compose.runtime.remember { mutableStateOf(initial) }
            // The real form scrolls; without a scroll container performScrollTo has nothing to scroll.
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                AdvancedSettingsSection(c, options) { options = it; onChange(it) }
            }
        }

    private fun expand() { rule.onNodeWithText(s(R.string.advanced_show)).performClick() }

    @Test fun it_starts_collapsed() {
        show()
        rule.onNodeWithText(s(R.string.advanced_title)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText(s(R.string.advanced_resolution)).fetchSemanticsNodes().size)
    }

    @Test fun showing_it_reveals_the_video_and_audio_controls() {
        show(); expand()
        listOf(R.string.advanced_resolution, R.string.advanced_frame_rate, R.string.advanced_profile, R.string.advanced_level,
            R.string.advanced_audio_bitrate, R.string.advanced_sample_rate, R.string.advanced_channels)
            .forEach { rule.onNodeWithText(s(it)).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText(s(R.string.advanced_hide)).assertIsDisplayed()
    }

    @Test fun nothing_is_shown_when_nothing_is_encoded() {
        show(c = AdvancedChoices())
        assertEquals(0, rule.onAllNodesWithText(s(R.string.advanced_title)).fetchSemanticsNodes().size)
    }

    @Test fun a_single_pixel_format_is_not_a_choice() {
        show(); expand()
        assertEquals(0, rule.onAllNodesWithText(s(R.string.advanced_pixel_format)).fetchSemanticsNodes().size)
    }

    @Test fun choosing_a_resolution_is_reported() {
        var got: AdvancedOptions? = null
        show(onChange = { got = it }); expand()
        rule.onNodeWithText(s(R.string.advanced_resolution)).performScrollTo().performClick()
        rule.onNodeWithText("480p").performClick()
        assertEquals(480, got?.resolutionHeight)
    }

    @Test fun every_dropdown_starts_at_the_preset_default() {
        show(); expand()
        assertEquals(true, rule.onAllNodesWithText(s(R.string.advanced_preset_default)).fetchSemanticsNodes().size >= 5)
    }

    @Test fun crf_is_offered_as_a_quality_mode_and_shows_its_value() {
        var got: AdvancedOptions? = null
        show(onChange = { got = it }); expand()
        rule.onNodeWithText(s(R.string.advanced_quality)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.advanced_quality_crf)).performClick()
        assertEquals(VideoQuality.Crf(23), got?.quality)
        rule.onNodeWithText(s(R.string.advanced_crf_value, 23)).performScrollTo().assertIsDisplayed()
    }

    @Test fun crf_is_not_offered_for_a_bitrate_only_encoder() {
        show(c = choices.copy(crfRange = null)); expand()
        rule.onNodeWithText(s(R.string.advanced_quality)).performScrollTo().performClick()
        assertEquals(0, rule.onAllNodesWithText(s(R.string.advanced_quality_crf)).fetchSemanticsNodes().size)
        rule.onNodeWithText(s(R.string.advanced_quality_bitrate)).assertIsDisplayed()
    }

    @Test fun a_bitrate_is_typed_in_kbps_and_reported() {
        var got: AdvancedOptions? = null
        show(initial = AdvancedOptions(quality = VideoQuality.Bitrate(2000)), onChange = { got = it }); expand()
        rule.onNodeWithText(s(R.string.advanced_bitrate_kbps)).performScrollTo().performTextReplacement("3500")
        assertEquals(VideoQuality.Bitrate(3500), got?.quality)
    }

    @Test fun going_back_to_the_preset_default_clears_the_override() {
        var got: AdvancedOptions? = AdvancedOptions(quality = VideoQuality.Crf(20))
        show(initial = AdvancedOptions(quality = VideoQuality.Crf(20)), onChange = { got = it }); expand()
        rule.onNodeWithText(s(R.string.advanced_quality_crf)).performScrollTo().performClick()
        // Several fields show "Preset default"; the menu entry is the one inside the popup.
        rule.onNode(hasText(s(R.string.advanced_preset_default)) and hasAnyAncestor(isPopup())).performClick()
        assertNull(got?.quality)
    }

    @Test fun audio_controls_report_their_choices() {
        var got: AdvancedOptions? = null
        show(onChange = { got = it }); expand()
        rule.onNodeWithText(s(R.string.advanced_channels)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.advanced_mono)).performClick()
        assertEquals(1, got?.channels)
    }
}
