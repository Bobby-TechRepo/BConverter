package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportRegistryTest {
    private val registry = ExportRegistry()
    private fun job(id: String = "j1") = ExportJob(
        id, "title",
        ExportTask.Extract(AudioExtractionRequest(id, ResolvedInput(File("in"), "in", 1, true), TestMedia.metadata(), File("out"),
            AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy))),
        Uri.parse("content://d/1"),
    )

    @Test fun a_registered_job_starts_queued_and_can_be_found() {
        val status = registry.register(job())
        assertEquals(JobStatus.Queued, status.value); assertEquals("title", registry.job("j1")!!.title)
        assertEquals(JobStatus.Queued, registry.status("j1")!!.value)
    }

    @Test fun updates_are_published_to_the_status_flow() {
        val status = registry.register(job()); registry.update("j1", JobStatus.Running(0.5f))
        assertEquals(JobStatus.Running(0.5f), status.value)
    }

    @Test fun unknown_jobs_are_absent() { assertNull(registry.job("x")); assertNull(registry.status("x")); assertFalse(registry.isDetached("x")) }

    @Test fun a_job_can_be_detached_from_its_screen() {
        registry.register(job()); assertFalse(registry.isDetached("j1")); registry.detach("j1"); assertTrue(registry.isDetached("j1"))
    }

    @Test fun removing_forgets_the_job() { registry.register(job()); registry.remove("j1"); assertNull(registry.job("j1")) }

    @Test fun only_finished_states_are_terminal() {
        assertFalse(JobStatus.Queued.isTerminal); assertFalse(JobStatus.Running(0.1f).isTerminal); assertFalse(JobStatus.Publishing.isTerminal)
        listOf(JobStatus.Completed, JobStatus.Failed(JobFailure.SaveFailed), JobStatus.Cancelled).forEach { assertTrue(it.isTerminal) }
    }

    @Test fun statuses_track_every_live_job_until_it_is_removed() {
        registry.register(job("a")); registry.register(job("b")); registry.update("a", JobStatus.Running(0.2f))
        assertEquals(mapOf("a" to JobStatus.Running(0.2f), "b" to JobStatus.Queued), registry.statuses.value)
        registry.remove("a")
        assertEquals(setOf("b"), registry.statuses.value.keys)
    }

    @Test fun the_listener_hears_the_start_and_the_first_final_status_only() {
        val heard = mutableListOf<String>()
        val listened = ExportRegistry(object : ExportRegistry.Listener {
            override fun started(job: ExportJob) { heard += "start:${job.id}" }
            override fun finished(jobId: String, status: JobStatus) { heard += "end:$jobId:$status" }
        })
        listened.register(job()); listened.update("j1", JobStatus.Running(0.5f)); listened.update("j1", JobStatus.Completed)
        listened.update("j1", JobStatus.Cancelled)
        assertEquals(listOf("start:j1", "end:j1:Completed"), heard)
    }

    @Test fun job_records_map_tasks_and_final_statuses() {
        val record = JobRecords.started(job(), 42)
        assertEquals(JobKind.EXTRACT_AUDIO, record.kind); assertEquals(JobOutcome.RUNNING, record.outcome); assertEquals(42L, record.startedAtMs)
        assertEquals(JobOutcome.COMPLETED, JobRecords.outcomeOf(JobStatus.Completed))
        assertEquals(JobOutcome.CANCELLED, JobRecords.outcomeOf(JobStatus.Cancelled))
        assertEquals(JobOutcome.INTERRUPTED, JobRecords.outcomeOf(JobStatus.Failed(JobFailure.Interrupted)))
        assertEquals(JobOutcome.SAVE_FAILED, JobRecords.outcomeOf(JobStatus.Failed(JobFailure.SaveFailed)))
        assertNull(JobRecords.outcomeOf(JobStatus.Publishing))
    }
}
