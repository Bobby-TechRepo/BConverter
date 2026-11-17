package io.bsoft.bconverter.feature.jobs

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JobsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun item(id: String, phase: JobPhase) = JobItem(id, "Clip $id", JobKind.CONVERT, "content://d/$id", 1, null, phase)

    @Test fun running_jobs_can_be_cancelled_and_saved_ones_shared() {
        var cancelled: String? = null
        var shared: String? = null
        rule.setContent {
            BConverterTheme {
                JobsScreen(
                    JobsUiState(loading = false, active = listOf(item("a", JobPhase.Running(0.25f))), finished = listOf(item("b", JobPhase.Ended(JobOutcome.COMPLETED)))),
                    onBack = {}, onCancel = { cancelled = it }, onShare = { shared = it.id }, onClearFinished = {},
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.jobs_running, 25)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.jobs_cancel)).performClick()
        rule.onNodeWithText(context.getString(R.string.jobs_share)).performClick()
        assertEquals("a", cancelled); assertEquals("b", shared)
    }

    @Test fun an_empty_history_explains_itself() {
        rule.setContent { BConverterTheme { JobsScreen(JobsUiState(loading = false), {}, {}, {}, {}) } }
        rule.onNodeWithText(context.getString(R.string.jobs_empty)).assertIsDisplayed()
    }

    @Test fun an_interrupted_job_says_so() {
        rule.setContent {
            BConverterTheme { JobsScreen(JobsUiState(loading = false, finished = listOf(item("c", JobPhase.Ended(JobOutcome.INTERRUPTED)))), {}, {}, {}, {}) }
        }
        rule.onNodeWithText(context.getString(R.string.jobs_interrupted)).assertIsDisplayed()
    }
}
