package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.repository.ExportScheduler
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Runs jobs immediately in a test scope, mimicking WorkManager (cancel = cancel the coroutine). */
class DirectExportScheduler(private val scope: CoroutineScope, private val executor: ExportJobExecutor) : ExportScheduler {
    private val running = mutableMapOf<String, Job>()
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override fun enqueue(jobId: String) {
        enqueued += jobId
        running[jobId] = scope.launch { executor.execute(jobId) }
    }

    override fun cancel(jobId: String) {
        cancelled += jobId
        running[jobId]?.cancel()
    }
}
