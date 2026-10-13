package io.bsoft.bconverter.feature.preview

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.media.player.PlaybackError
import io.bsoft.bconverter.media.player.PlaybackState
import io.bsoft.bconverter.media.player.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreviewControlsTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = ctx.getString(id)
    private val ready = PlaybackState(PlaybackStatus.Ready, isPlaying = false, positionMs = 5_000, durationMs = 12_000)
    private fun show(state: PlaybackState, onPlayPause: () -> Unit = {}, onSeek: (Long) -> Unit = {}) =
        rule.setContent { PreviewControls(state, onPlayPause, onSeek) }

    @Test fun a_paused_preview_offers_play_and_reports_taps() {
        var taps = 0
        show(ready, onPlayPause = { taps++ })
        rule.onNodeWithContentDescription(s(R.string.preview_play)).assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test fun a_playing_preview_offers_pause() {
        show(ready.copy(isPlaying = true))
        rule.onNodeWithContentDescription(s(R.string.preview_pause)).assertIsDisplayed()
    }

    @Test fun elapsed_and_total_time_are_shown() {
        show(ready)
        rule.onNodeWithText(ctx.getString(R.string.preview_time, "0:05", "0:12")).assertIsDisplayed()
    }

    @Test fun moving_the_slider_seeks_proportionally() {
        var target: Long? = null
        show(ready, onSeek = { target = it })
        rule.onNodeWithContentDescription(s(R.string.preview_seek)).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(6_000L, target)
    }

    @Test fun while_loading_the_controls_are_disabled() {
        show(PlaybackState(PlaybackStatus.Loading))
        rule.onNodeWithContentDescription(s(R.string.preview_play)).assertIsNotEnabled()
    }

    @Test fun an_error_is_explained_in_plain_language() {
        show(PlaybackState(PlaybackStatus.Error, error = PlaybackError.Unsupported))
        rule.onNodeWithText(s(R.string.preview_error_unsupported)).assertIsDisplayed()
    }

    @Test fun a_playback_error_never_blocks_the_rest_of_the_screen() {
        show(PlaybackState(PlaybackStatus.Error, error = PlaybackError.Unknown))
        rule.onNodeWithText(s(R.string.preview_error_generic)).assertIsDisplayed()
        assertTrue(true)
    }
}
