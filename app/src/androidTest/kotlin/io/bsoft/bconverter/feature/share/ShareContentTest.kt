package io.bsoft.bconverter.feature.share

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.share.ShareAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** "Open with BConverter" (step 25). */
class ShareContentTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val a = Uri.parse("content://x/a")
    private val b = Uri.parse("content://x/b")

    @Test fun one_video_can_go_to_any_tool() {
        val picked = mutableListOf<ShareAction>()
        rule.setContent { ShareContent(listOf(a), mapOf(a to "beach.mp4"), { picked += it }, {}) }
        rule.onNodeWithText("beach.mp4").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.share_edit)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.home_extract_audio)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.home_extract_subtitles)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.home_convert_video)).performClick()
        assertEquals(listOf(ShareAction.CONVERT), picked)
    }

    @Test fun several_videos_can_start_a_project_or_be_converted_together() {
        val picked = mutableListOf<ShareAction>()
        rule.setContent { ShareContent(listOf(a, b), emptyMap(), { picked += it }, {}) }
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.share_count, 2, 2)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText(s(R.string.home_extract_audio))).fetchSemanticsNodes().size)
        rule.onNodeWithText(s(R.string.share_convert_all)).performClick()
        rule.onNodeWithText(s(R.string.share_edit)).performClick()
        assertEquals(listOf(ShareAction.CONVERT_ALL, ShareAction.EDIT), picked)
    }

    @Test fun an_empty_share_says_so() {
        rule.setContent { ShareContent(emptyList(), emptyMap(), {}, {}) }
        rule.onNodeWithText(s(R.string.share_nothing)).assertIsDisplayed()
    }
}
