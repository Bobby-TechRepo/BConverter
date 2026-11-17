package io.bsoft.bconverter.feature.audioextractor

import io.bsoft.bconverter.domain.model.SavedOutput
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.VideoStream
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudioExtractorContentTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = ctx.getString(id)

    private fun source(audioTracks: Int = 1) = SourceInfo(
        "holiday.mp4", 7_340_032,
        MediaMetadata(
            12_345, ContainerFormat.MP4, listOf(VideoStream(0, "h264", 1920, 1080, 30.0)),
            List(audioTracks) { AudioStream(it + 1, "aac", 44100, 2) },
        ),
    )
    private fun configure(tracks: Int = 1, canCopy: Boolean = true) = AudioExtractorUiState(
        stage = ConverterStage.Configure, source = source(tracks),
        form = ExtractorForm(0, AudioFormat.M4A, true, "holiday-audio"),
        settings = AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy),
        formats = AudioFormat.entries, canCopy = canCopy,
    )
    private fun show(state: AudioExtractorUiState, actions: ExtractorActions = ExtractorActions()) =
        rule.setContent { AudioExtractorContent(state, actions) }

    @Test fun selection_offers_the_picker_and_reports_taps() {
        var tapped = false
        show(AudioExtractorUiState(), ExtractorActions(onSelectInput = { tapped = true }))
        rule.onNodeWithText(s(R.string.select_video)).assertIsDisplayed().performClick()
        assertTrue(tapped)
    }

    @Test fun selection_shows_the_current_problem() {
        show(AudioExtractorUiState(message = ConverterMessage.NoAudioStream))
        rule.onNodeWithText(s(R.string.msg_no_audio)).assertIsDisplayed()
    }

    @Test fun probing_says_the_file_is_being_analysed() {
        show(AudioExtractorUiState(stage = ConverterStage.Probing))
        rule.onNodeWithText(s(R.string.converter_analyzing)).assertIsDisplayed()
    }

    @Test fun the_form_shows_the_file_and_starts_extraction() {
        var started = false
        show(configure(), ExtractorActions(onStart = { started = true }))
        rule.onNodeWithText("holiday.mp4").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.extractor_extract)).performScrollTo().performClick()
        assertTrue(started)
    }

    @Test fun the_copy_switch_reports_the_new_value() {
        var value: Boolean? = null
        show(configure(), ExtractorActions(onPreferCopy = { value = it }))
        rule.onNodeWithText(s(R.string.extractor_copy)).performScrollTo().performClick()
        assertEquals(false, value)
    }

    @Test fun the_copy_switch_row_is_at_least_48dp_tall_to_tap() {
        show(configure())
        rule.onNode(androidx.compose.ui.test.isToggleable()).performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test fun when_copy_is_impossible_the_user_is_told_why() {
        show(configure(canCopy = false))
        rule.onNodeWithText(s(R.string.extractor_reencode_hint)).performScrollTo().assertIsDisplayed()
    }

    @Test fun the_track_picker_only_appears_for_several_tracks() {
        show(configure(tracks = 1)); rule.onNodeWithText(s(R.string.extractor_track)).assertDoesNotExist()
    }

    @Test fun several_tracks_offer_a_picker() {
        show(configure(tracks = 2)); rule.onNodeWithText(s(R.string.extractor_track)).assertIsDisplayed()
    }

    @Test fun extraction_shows_percentage_and_can_be_cancelled() {
        var cancelled = false
        show(
            configure().copy(stage = ConverterStage.Extracting, processing = ProcessingState.Running(0.4f, "Converting")),
            ExtractorActions(onCancel = { cancelled = true }),
        )
        rule.onNodeWithText(ctx.getString(R.string.extractor_extracting, 40)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.converter_cancel)).performClick()
        assertTrue(cancelled)
    }

    @Test fun completion_confirms_and_offers_another_extraction() {
        var again = false
        show(configure().copy(stage = ConverterStage.Completed), ExtractorActions(onExtractAnother = { again = true }))
        rule.onNodeWithText(s(R.string.extractor_saved)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.extractor_extract_another)).performClick()
        assertTrue(again)
    }

    // ---- sharing (spec Phase 16) ----
    @Test fun a_finished_extraction_can_be_shared() {
        val saved = SavedOutput(Uri.parse("content://flow/audio"), "audio/mp4")
        var shared: SavedOutput? = null
        show(configure().copy(stage = ConverterStage.Completed, saved = saved), ExtractorActions(onShare = { shared = it }))
        rule.onNodeWithText(s(R.string.share)).performClick()
        assertEquals(saved, shared)
    }
}
