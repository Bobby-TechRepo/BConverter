package io.bsoft.bconverter.data.repository

import android.net.Uri
import io.bsoft.bconverter.data.database.ExportJobDao
import io.bsoft.bconverter.data.database.ExportJobEntity
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomJobHistoryTest {
    private class FakeDao : ExportJobDao {
        val rows = MutableStateFlow<List<ExportJobEntity>>(emptyList())
        override suspend fun upsert(entity: ExportJobEntity) = rows.update { list -> list.filterNot { it.id == entity.id } + entity }
        override fun observeRecent(): Flow<List<ExportJobEntity>> = rows
        override suspend fun finish(id: String, outcome: String, atMs: Long) =
            rows.update { list -> list.map { if (it.id == id) it.copy(outcome = outcome, finishedAtMs = atMs) else it } }
        override suspend fun markInterrupted(processStartMs: Long, atMs: Long) = Unit
        override suspend fun clearFinished() = Unit
    }

    private fun job(id: String = "j1") = ExportJob(
        id, "Song",
        ExportTask.Extract(AudioExtractionRequest(id, ResolvedInput(File("in"), "in", 1, true), TestMedia.metadata(), File("out"),
            AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy))),
        Uri.parse("content://d/1"),
    )

    @Test fun the_registry_records_a_job_when_it_starts_and_when_it_ends() = runTest {
        val dao = FakeDao()
        var clock = 100L
        val history = RoomJobHistory(dao, this) { clock }
        val registry = ExportRegistry(history)
        registry.register(job()); advanceUntilIdle()
        val started = history.records().first().single()
        assertEquals(JobOutcome.RUNNING, started.outcome); assertEquals(JobKind.EXTRACT_AUDIO, started.kind)
        assertEquals("content://d/1", started.destination); assertEquals(100L, started.startedAtMs); assertNull(started.finishedAtMs)

        clock = 250L
        registry.update("j1", JobStatus.Running(0.5f)); advanceUntilIdle()
        assertEquals(JobOutcome.RUNNING, history.records().first().single().outcome)
        registry.update("j1", JobStatus.Failed(JobFailure.SaveFailed)); advanceUntilIdle()
        val ended = history.records().first().single()
        assertEquals(JobOutcome.SAVE_FAILED, ended.outcome); assertEquals(250L, ended.finishedAtMs)
    }

    @Test fun only_the_first_final_status_is_recorded() = runTest {
        val dao = FakeDao()
        val history = RoomJobHistory(dao, this) { 1L }
        val registry = ExportRegistry(history)
        registry.register(job()); registry.update("j1", JobStatus.Cancelled); registry.update("j1", JobStatus.Completed); advanceUntilIdle()
        assertEquals(JobOutcome.CANCELLED, history.records().first().single().outcome)
    }

    @Test fun unknown_stored_names_read_as_safe_defaults() = runTest {
        val dao = FakeDao()
        dao.rows.value = listOf(ExportJobEntity("x", "t", "FUTURE_KIND", "content://d/x", "FUTURE_OUTCOME", 1, null))
        val record = RoomJobHistory(dao, this).records().first().single()
        assertEquals(JobKind.CONVERT, record.kind); assertEquals(JobOutcome.INTERRUPTED, record.outcome)
    }
}
