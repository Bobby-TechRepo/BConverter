package io.bsoft.bconverter.feature.converter

import androidx.compose.ui.test.assertDoesNotExist
import io.bsoft.bconverter.domain.model.SavedOutput
import android.net.Uri
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConverterContentTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = ctx.getString(id)

    private val metadata = MediaMetadata(
        12_345, ContainerFormat.MP4, listOf(VideoStream(0, "h264", 1920, 1080, 30.0)), listOf(AudioStream(1, "aac", 44100, 2)),
    )
    private val source = SourceInfo("holiday.mp4", 7_340_032, metadata)
    private val settings = ConversionSettings(ContainerFormat.MP4, VideoPlan.Copy, AudioPlan.Copy)
    private fun configure(settings: ConversionSettings? = this.settings, issues: List<ConversionIssue> = emptyList()) = ConverterUiState(
        stage = ConverterStage.Configure, source = source, settings = settings, issues = issues,
        form = ConverterForm(ConversionPreset.BALANCED, ContainerFormat.MP4, null, null, "holiday-converted"),
        containers = listOf(ContainerFormat.MP4, ContainerFormat.MKV),
        videoCodecs = listOf(VideoCodec.H264, VideoCodec.HEVC), audioCodecs = listOf(AudioCodec.AAC, AudioCodec.MP3),
    )
    private fun show(state: ConverterUiState, actions: ConverterActions = ConverterActions()) =
        rule.setContent { ConverterContent(state, actions) }

    @Test fun selection_offers_the_picker_and_reports_taps() {
        var tapped = false
        show(ConverterUiState(), ConverterActions(onSelectInput = { tapped = true }))
        rule.onNodeWithText(s(R.string.select_video)).assertIsDisplayed().performClick()
        assertTrue(tapped)
    }

    @Test fun selection_shows_the_current_problem() {
        show(ConverterUiState(message = ConverterMessage.ProbeFailed))
        rule.onNodeWithText(s(R.string.msg_probe_failed)).assertIsDisplayed()
    }

    @Test fun probing_says_the_file_is_being_analysed() {
        show(ConverterUiState(stage = ConverterStage.Probing))
        rule.onNodeWithText(s(R.string.converter_analyzing)).assertIsDisplayed()
    }

    @Test fun the_form_summarises_the_source() {
        show(configure())
        rule.onNodeWithText("holiday.mp4").assertIsDisplayed()
        rule.onNodeWithText(ctx.getString(R.string.converter_summary_duration, "0:12")).assertIsDisplayed()
    }

    @Test fun presets_are_offered_and_selection_is_reported() {
        var chosen: ConversionPreset? = null
        show(configure(), ConverterActions(onPreset = { chosen = it }))
        listOf(R.string.preset_small_file, R.string.preset_balanced, R.string.preset_high_quality, R.string.preset_original_quality)
            .forEach { rule.onNodeWithText(s(it)).assertIsDisplayed() }
        rule.onNodeWithText(s(R.string.preset_small_file)).performClick()
        assertEquals(ConversionPreset.SMALL_FILE, chosen)
    }

    @Test fun the_output_name_is_editable() {
        var name = ""
        show(configure(), ConverterActions(onOutputName = { name = it }))
        rule.onNodeWithText("holiday-converted").performTextReplacement("trip")
        assertEquals("trip", name)
    }

    @Test fun convert_starts_the_flow() {
        var started = false
        show(configure(), ConverterActions(onStart = { started = true }))
        rule.onNodeWithText(s(R.string.converter_convert)).performScrollTo().performClick()
        assertTrue(started)
    }

    @Test fun without_usable_settings_convert_is_disabled_and_explained() {
        show(configure(settings = null))
        rule.onNodeWithText(s(R.string.converter_convert)).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText(s(R.string.converter_no_settings)).performScrollTo().assertIsDisplayed()
    }

    @Test fun validation_issues_are_shown_in_plain_language() {
        show(configure(issues = listOf(ConversionIssue.InvalidOutputName)))
        rule.onNodeWithText(s(R.string.issue_output_name)).performScrollTo().assertIsDisplayed()
    }

    @Test fun conversion_shows_percentage_and_can_be_cancelled() {
        var cancelled = false
        show(
            configure().copy(stage = ConverterStage.Converting, processing = ProcessingState.Running(0.4f, "Converting")),
            ConverterActions(onCancel = { cancelled = true }),
        )
        rule.onNodeWithText(ctx.getString(R.string.converter_converting, 40)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.converter_cancel)).performClick()
        assertTrue(cancelled)
    }

    @Test fun completion_confirms_and_offers_another_conversion() {
        var again = false
        show(configure().copy(stage = ConverterStage.Completed), ConverterActions(onConvertAnother = { again = true }))
        rule.onNodeWithText(s(R.string.converter_saved)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.converter_convert_another)).performClick()
        assertTrue(again)
    }

    // ---- layouts (spec §37): the tablet form is two panes, not the phone column stretched ----
    private fun showWithSmallestWidth(widthDp: Int, state: ConverterUiState) = rule.setContent {
        val base = LocalConfiguration.current
        val config = Configuration(base).apply { smallestScreenWidthDp = widthDp }
        CompositionLocalProvider(LocalConfiguration provides config) { ConverterContent(state, ConverterActions()) }
    }

    @Test fun on_a_tablet_the_source_sits_beside_the_options() {
        showWithSmallestWidth(720, configure())
        val source = rule.onNodeWithText("holiday.mp4").getBoundsInRoot()
        val convert = rule.onNodeWithText(s(R.string.converter_convert)).getBoundsInRoot()
        assertTrue("source ${source.right} should end before the options start at ${convert.left}", source.right <= convert.left)
    }

    @Test fun on_a_phone_the_source_is_above_the_options() {
        showWithSmallestWidth(360, configure())
        val source = rule.onNodeWithText("holiday.mp4").getBoundsInRoot()
        val container = rule.onNodeWithText(s(R.string.converter_container)).getBoundsInRoot()
        assertTrue(source.bottom <= container.top)
    }

    // ---- sharing (spec Phase 16) ----
    private val saved = SavedOutput(Uri.parse("content://flow/out"), "video/mp4")

    @Test fun a_finished_conversion_can_be_shared() {
        var shared: SavedOutput? = null
        show(configure().copy(stage = ConverterStage.Completed, saved = saved), ConverterActions(onShare = { shared = it }))
        rule.onNodeWithText(s(R.string.share)).performClick()
        assertEquals(saved, shared)
    }

    @Test fun without_a_saved_file_there_is_nothing_to_share() {
        show(configure().copy(stage = ConverterStage.Completed))
        rule.onNodeWithText(s(R.string.share)).assertDoesNotExist()
    }
}
