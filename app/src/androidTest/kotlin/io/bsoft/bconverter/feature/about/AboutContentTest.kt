package io.bsoft.bconverter.feature.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.about.AppInfo
import io.bsoft.bconverter.domain.about.ThirdPartyNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutContentTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg a: Any) = context.getString(id, *a)
    private val notices = listOf(ThirdPartyNotice("FFmpeg", "LGPL-2.1-or-later", "Converts media.", "https://ffmpeg.org/legal.html"))

    @Test fun the_version_and_engine_are_shown() {
        rule.setContent { AboutContent(AppInfo("1.2.3", 45, "n8.1.2"), notices, {}, {}) }
        rule.onNodeWithText(str(R.string.about_version, "1.2.3 (45)")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.about_engine_version, "n8.1.2")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.about_engine_licence)).assertIsDisplayed()
    }

    @Test fun the_screen_works_before_the_info_has_loaded() {
        rule.setContent { AboutContent(null, notices, {}, {}) }
        rule.onNodeWithText("BConverter").assertIsDisplayed()
    }

    @Test fun every_notice_shows_its_licence_and_opens_its_page() {
        var opened: String? = null
        rule.setContent { AboutContent(null, notices, {}, { opened = it }) }
        rule.onNodeWithText(str(R.string.about_notice_license, "LGPL-2.1-or-later")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("FFmpeg").performScrollTo().performClick()
        assertEquals("https://ffmpeg.org/legal.html", opened)
    }
}
