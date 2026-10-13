package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.repository.ExportScheduler
import kotlinx.coroutines.flow.StateFlow

/** What a screen uses to start, observe, cancel and let go of a background job. */
class ExportJobLauncher(private val registry: ExportRegistry, private val scheduler: ExportScheduler) {
    /** Registers the job, schedules it, and returns its status flow. */
    fun launch(job: ExportJob): StateFlow<JobStatus> {
        val status = registry.register(job)
        scheduler.enqueue(job.id)
        return status
    }

    fun cancel(jobId: String) = scheduler.cancel(jobId)

    /** The screen is gone: the executor releases the job's input and forgets it when it finishes. */
    fun detach(jobId: String) = registry.detach(jobId)

    /** The screen has seen the job's final status. */
    fun finish(jobId: String) = registry.remove(jobId)
}
