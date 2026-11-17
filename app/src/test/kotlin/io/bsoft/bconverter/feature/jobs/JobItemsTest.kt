package io.bsoft.bconverter.feature.jobs

import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome
import io.bsoft.bconverter.domain.usecase.JobRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobItemsTest {
    private fun record(id: String = "j", outcome: JobOutcome = JobOutcome.RUNNING, started: Long = 1) =
        JobRecord(id, "Clip $id", JobKind.CONVERT, "content://d/$id", outcome, started, if (outcome == JobOutcome.RUNNING) null else started + 5)

    @Test fun live_progress_wins_over_the_stored_running_row() {
        assertEquals(JobPhase.Running(0.4f), JobItems.of(record(), JobStatus.Running(0.4f)).phase)
        assertEquals(JobPhase.Queued, JobItems.of(record(), JobStatus.Queued).phase)
        assertEquals(JobPhase.Saving, JobItems.of(record(), JobStatus.Publishing).phase)
    }

    @Test fun a_live_final_status_shows_before_the_row_catches_up() {
        assertEquals(JobPhase.Ended(JobOutcome.COMPLETED), JobItems.of(record(), JobStatus.Completed).phase)
        assertEquals(JobPhase.Ended(JobOutcome.SAVE_FAILED), JobItems.of(record(), JobStatus.Failed(JobFailure.SaveFailed)).phase)
    }

    @Test fun a_running_row_without_live_status_is_running_without_progress() {
        assertEquals(JobPhase.Running(null), JobItems.of(record(), null).phase)
    }

    @Test fun stored_outcomes_are_shown_as_they_are() {
        assertEquals(JobPhase.Ended(JobOutcome.INTERRUPTED), JobItems.of(record(outcome = JobOutcome.INTERRUPTED), null).phase)
    }

    @Test fun only_completed_jobs_can_be_shared() {
        assertTrue(JobItems.of(record(outcome = JobOutcome.COMPLETED), null).canShare)
        assertFalse(JobItems.of(record(outcome = JobOutcome.FAILED), null).canShare)
        assertFalse(JobItems.of(record(), JobStatus.Running(0.9f)).canShare)
    }

    @Test fun the_state_splits_active_from_finished_keeping_order() {
        val state = JobItems.state(
            listOf(record("a", started = 3), record("b", JobOutcome.COMPLETED, 2), record("c", started = 1)),
            mapOf("c" to JobStatus.Cancelled),
        )
        assertFalse(state.loading)
        assertEquals(listOf("a"), state.active.map { it.id })
        assertEquals(listOf("b", "c"), state.finished.map { it.id })
        assertFalse(state.isEmpty)
        assertTrue(JobItems.state(emptyList(), emptyMap()).isEmpty)
    }
}
