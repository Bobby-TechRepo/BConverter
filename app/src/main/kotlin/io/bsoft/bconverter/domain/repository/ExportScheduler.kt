package io.bsoft.bconverter.domain.repository

/** Hands a registered job to the background executor (WorkManager in production). */
interface ExportScheduler {
    fun enqueue(jobId: String)
    fun cancel(jobId: String)
}
