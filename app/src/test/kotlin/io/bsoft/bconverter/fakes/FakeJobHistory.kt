package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.usecase.JobHistory
import io.bsoft.bconverter.domain.usecase.JobOutcome
import io.bsoft.bconverter.domain.usecase.JobRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [JobHistory], newest first like the Room one. */
class FakeJobHistory(initial: List<JobRecord> = emptyList()) : JobHistory {
    val rows = MutableStateFlow(initial)
    override fun records(): Flow<List<JobRecord>> = rows
    override suspend fun save(record: JobRecord) = rows.update { list -> (list.filterNot { it.id == record.id } + record).sortedByDescending { it.startedAtMs } }
    override suspend fun finish(id: String, outcome: JobOutcome, atMs: Long) =
        rows.update { list -> list.map { if (it.id == id) it.copy(outcome = outcome, finishedAtMs = atMs) else it } }
    override suspend fun markInterrupted(processStartMs: Long, atMs: Long) = rows.update { list ->
        list.map { if (it.outcome == JobOutcome.RUNNING && it.startedAtMs < processStartMs) it.copy(outcome = JobOutcome.INTERRUPTED, finishedAtMs = atMs) else it }
    }
    override suspend fun clearFinished() = rows.update { list -> list.filter { it.outcome == JobOutcome.RUNNING } }
}
