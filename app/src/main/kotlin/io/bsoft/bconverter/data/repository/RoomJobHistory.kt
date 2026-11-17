package io.bsoft.bconverter.data.repository

import io.bsoft.bconverter.core.diagnostics.AppLog
import io.bsoft.bconverter.data.database.ExportJobDao
import io.bsoft.bconverter.data.database.ExportJobEntity
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.JobHistory
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome
import io.bsoft.bconverter.domain.usecase.JobRecord
import io.bsoft.bconverter.domain.usecase.JobRecords
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Room-backed [JobHistory]; also the [ExportRegistry.Listener] that records jobs as they start and end. Writes happen on
 * [scope] (the app scope, IO) so the registry, which is called from the export worker, never waits for the database.
 */
class RoomJobHistory(
    private val dao: ExportJobDao,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : JobHistory, ExportRegistry.Listener {
    override fun records(): Flow<List<JobRecord>> = dao.observeRecent().map { rows -> rows.map { it.toRecord() } }
    override suspend fun save(record: JobRecord) = dao.upsert(record.toEntity())
    override suspend fun finish(id: String, outcome: JobOutcome, atMs: Long) = dao.finish(id, outcome.name, atMs)
    override suspend fun markInterrupted(processStartMs: Long, atMs: Long) = dao.markInterrupted(processStartMs, atMs)
    override suspend fun clearFinished() = dao.clearFinished()

    override fun started(job: ExportJob) {
        val record = JobRecords.started(job, now())
        write { save(record) }
    }

    override fun finished(jobId: String, status: JobStatus) {
        val outcome = JobRecords.outcomeOf(status) ?: return
        val at = now()
        write { finish(jobId, outcome, at) }
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLog.nonFatal("Jobs", "Job history write failed", e) }
        }
    }

    private fun ExportJobEntity.toRecord() = JobRecord(
        id, title, JobKind.entries.firstOrNull { it.name == kind } ?: JobKind.CONVERT, destination,
        JobOutcome.entries.firstOrNull { it.name == outcome } ?: JobOutcome.INTERRUPTED, startedAtMs, finishedAtMs,
    )

    private fun JobRecord.toEntity() = ExportJobEntity(id, title, kind.name, destination, outcome.name, startedAtMs, finishedAtMs)
}
