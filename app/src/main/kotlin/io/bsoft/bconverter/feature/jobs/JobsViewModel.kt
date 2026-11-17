package io.bsoft.bconverter.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.diagnostics.AppLog
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.JobHistory
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome
import io.bsoft.bconverter.domain.usecase.JobRecord
import io.bsoft.bconverter.domain.usecase.JobRecords
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a row shows. A live status from this process wins over the stored outcome, which may lag a moment behind it. */
sealed interface JobPhase {
    data object Queued : JobPhase
    /** [progress] is null when the job is running but this process has no live progress for it yet. */
    data class Running(val progress: Float?) : JobPhase
    data object Saving : JobPhase
    data class Ended(val outcome: JobOutcome) : JobPhase
}

data class JobItem(
    val id: String,
    val title: String,
    val kind: JobKind,
    val destination: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val phase: JobPhase,
) {
    val isActive: Boolean get() = phase !is JobPhase.Ended
    val canShare: Boolean get() = phase == JobPhase.Ended(JobOutcome.COMPLETED)
}

data class JobsUiState(
    val loading: Boolean = true,
    val active: List<JobItem> = emptyList(),
    val finished: List<JobItem> = emptyList(),
    val loadFailed: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && active.isEmpty() && finished.isEmpty()
}

object JobItems {
    fun of(record: JobRecord, live: JobStatus?): JobItem {
        val phase = when {
            live != null && !live.isTerminal -> when (live) {
                JobStatus.Queued -> JobPhase.Queued
                is JobStatus.Running -> JobPhase.Running(live.progress)
                JobStatus.Publishing -> JobPhase.Saving
                else -> JobPhase.Running(null)
            }
            live != null -> JobPhase.Ended(JobRecords.outcomeOf(live) ?: record.outcome)
            record.outcome == JobOutcome.RUNNING -> JobPhase.Running(null)
            else -> JobPhase.Ended(record.outcome)
        }
        return JobItem(record.id, record.title, record.kind, record.destination, record.startedAtMs, record.finishedAtMs, phase)
    }

    fun state(records: List<JobRecord>, statuses: Map<String, JobStatus>): JobsUiState {
        val items = records.map { of(it, statuses[it.id]) }
        return JobsUiState(loading = false, active = items.filter { it.isActive }, finished = items.filterNot { it.isActive })
    }
}

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val history: JobHistory,
    registry: ExportRegistry,
    private val launcher: ExportJobLauncher,
) : ViewModel() {
    val state: StateFlow<JobsUiState> = combine(history.records(), registry.statuses) { records, statuses -> JobItems.state(records, statuses) }
        .catch { e -> AppLog.nonFatal("Jobs", "Job history could not be read", e); emit(JobsUiState(loading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobsUiState())

    fun cancel(id: String) = launcher.cancel(id)

    fun clearFinished() {
        viewModelScope.launch {
            try { history.clearFinished() } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLog.nonFatal("Jobs", "Clearing finished jobs failed", e) }
        }
    }
}
