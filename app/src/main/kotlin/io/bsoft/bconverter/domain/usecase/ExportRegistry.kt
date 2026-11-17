package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.JobStatus
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Application-wide, in-memory record of export jobs. A job outlives the screen that created it; the screen
 * observes [status] and may [detach] when it goes away, after which the executor cleans up for it.
 * Jobs cannot be persisted across process death (an FFmpeg run cannot resume), so WorkManager restarts
 * find nothing here and report the job as interrupted. The [listener] (step 17) keeps a durable history of what happened.
 */
class ExportRegistry(private val listener: Listener? = null) {
    /** Told when a job starts and when it reaches a final status; used for the persistent job history. */
    interface Listener {
        fun started(job: ExportJob)
        fun finished(jobId: String, status: JobStatus)
    }

    private class Entry(val job: ExportJob) {
        val status = MutableStateFlow<JobStatus>(JobStatus.Queued)
        @Volatile var detached = false
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val _statuses = MutableStateFlow<Map<String, JobStatus>>(emptyMap())

    /** The live status of every job this process knows, by id (for the Jobs screen). */
    val statuses: StateFlow<Map<String, JobStatus>> = _statuses.asStateFlow()

    fun register(job: ExportJob): StateFlow<JobStatus> {
        val entry = Entry(job)
        entries[job.id] = entry
        _statuses.update { it + (job.id to JobStatus.Queued) }
        listener?.started(job)
        return entry.status.asStateFlow()
    }

    fun job(id: String): ExportJob? = entries[id]?.job
    fun status(id: String): StateFlow<JobStatus>? = entries[id]?.status?.asStateFlow()

    fun update(id: String, status: JobStatus) {
        val entry = entries[id] ?: return
        val wasTerminal = entry.status.value.isTerminal
        entry.status.value = status
        _statuses.update { it + (id to status) }
        if (status.isTerminal && !wasTerminal) listener?.finished(id, status)
    }

    fun detach(id: String) { entries[id]?.detached = true }
    fun isDetached(id: String): Boolean = entries[id]?.detached == true

    fun remove(id: String) {
        entries.remove(id)
        _statuses.update { it - id }
    }
}
