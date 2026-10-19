package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.repository.ExportScheduler

/** Records what was scheduled without running anything. */
class RecordingExportScheduler : ExportScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun enqueue(jobId: String) { enqueued += jobId }
    override fun cancel(jobId: String) { cancelled += jobId }
}
