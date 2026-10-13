package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.JobStatus
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-wide, in-memory record of export jobs. A job outlives the screen that created it; the screen
 * observes [status] and may [detach] when it goes away, after which the executor cleans up for it.
 * Jobs cannot be persisted across process death (an FFmpeg run cannot resume), so WorkManager restarts
 * find nothing here and report the job as interrupted.
 */
class ExportRegistry {
    private class Entry(val job: ExportJob) {
        val status = MutableStateFlow<JobStatus>(JobStatus.Queued)
        @Volatile var detached = false
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun register(job: ExportJob): StateFlow<JobStatus> {
        val entry = Entry(job)
        entries[job.id] = entry
        return entry.status.asStateFlow()
    }

    fun job(id: String): ExportJob? = entries[id]?.job
    fun status(id: String): StateFlow<JobStatus>? = entries[id]?.status?.asStateFlow()
    fun update(id: String, status: JobStatus) { entries[id]?.status?.value = status }
    fun detach(id: String) { entries[id]?.detached = true }
    fun isDetached(id: String): Boolean = entries[id]?.detached == true
    fun remove(id: String) { entries.remove(id) }
}
