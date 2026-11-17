package io.bsoft.bconverter.feature.batch

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.usecase.BatchItem
import io.bsoft.bconverter.domain.usecase.BatchItemStatus
import io.bsoft.bconverter.domain.usecase.BatchOptions
import io.bsoft.bconverter.domain.usecase.BatchRun
import io.bsoft.bconverter.domain.usecase.BatchSkip
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The batch conversion screen (step 25). */
class BatchConvertContentTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun p(id: Int, n: Int) = context.resources.getQuantityString(id, n, n)
    private val a = Uri.parse("content://x/a")
    private val b = Uri.parse("content://x/b")
    private val containers = listOf(ContainerFormat.MP4, ContainerFormat.MKV)

    @Test fun with_nothing_picked_it_asks_for_videos() {
        var picked = 0
        rule.setContent { BatchConvertContent(BatchUiState(containers = containers), BatchActions(onPick = { picked++ })) }
        rule.onNodeWithText(s(R.string.batch_choose)).performClick()
        assertEquals(1, picked)
    }

    @Test fun the_list_shows_each_video_with_a_way_to_remove_it_and_one_setting_for_all() {
        val calls = mutableListOf<String>()
        val state = BatchUiState(listOf(PickedFile(a, "beach.mp4", 1_000_000), PickedFile(b, "party.mov")), BatchOptions(), containers)
        rule.setContent {
            BatchConvertContent(state, BatchActions(
                onRemove = { calls += "remove ${it.lastPathSegment}" }, onPreset = { calls += "preset $it" },
                onContainer = { calls += "container $it" }, onStart = { calls += "start" },
            ))
        }
        rule.onNodeWithText(p(R.plurals.batch_video_count, 2)).assertIsDisplayed()
        rule.onNodeWithText("beach.mp4").assertIsDisplayed()
        rule.onNodeWithContentDescription(s(R.string.batch_remove, "party.mov")).performClick()
        rule.onNodeWithText(s(R.string.preset_small_file)).performScrollTo().performClick()
        rule.onNodeWithText("MKV").performScrollTo().performClick()
        rule.onNodeWithText(p(R.plurals.batch_start, 2)).performScrollTo().performClick()
        assertEquals(listOf("remove b", "preset ${ConversionPreset.SMALL_FILE}", "container ${ContainerFormat.MKV}", "start"), calls)
    }

    @Test fun start_waits_until_the_formats_are_known() {
        rule.setContent { BatchConvertContent(BatchUiState(listOf(PickedFile(a, "a.mp4"))), BatchActions()) }
        rule.onNodeWithText(p(R.plurals.batch_start, 1)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun a_running_batch_shows_where_it_is_and_can_be_stopped() {
        var cancelled = false
        val run = BatchRun(listOf(BatchItem(a, BatchItemStatus.Converting(0.4f), "a.mp4"), BatchItem(b)), BatchOptions(), running = true)
        rule.setContent { BatchConvertContent(BatchUiState(run = run), BatchActions(onCancel = { cancelled = true })) }
        rule.onNodeWithText(s(R.string.batch_converting, 1, 2)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.batch_status_converting, 40)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.batch_status_waiting)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.converter_cancel)).performClick()
        assertEquals(true, cancelled)
    }

    @Test fun a_finished_batch_sums_up_says_why_files_were_left_out_and_shares_saved_ones() {
        val saved = SavedOutput(Uri.parse("content://dest/1"), "video/mp4")
        val shared = mutableListOf<SavedOutput>()
        val run = BatchRun(
            listOf(
                BatchItem(a, BatchItemStatus.Saved(saved, "a-converted.mp4"), "a.mp4"),
                BatchItem(b, BatchItemStatus.Skipped(BatchSkip.NO_VIDEO), "song.mp3"),
                BatchItem(Uri.parse("content://x/c"), BatchItemStatus.Failed(JobFailure.ConversionFailed), "c.mp4"),
            ),
            BatchOptions(), running = false,
        )
        rule.setContent { BatchConvertContent(BatchUiState(run = run), BatchActions(onShare = { shared += it })) }
        rule.onNodeWithText(s(R.string.batch_done_summary, 1, 2)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.batch_status_saved, "a-converted.mp4")).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.batch_skip_no_video)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.batch_failed)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.share)).performClick()
        assertEquals(listOf(saved), shared)
    }
}
