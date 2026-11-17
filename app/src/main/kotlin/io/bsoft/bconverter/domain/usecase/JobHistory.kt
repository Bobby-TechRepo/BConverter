package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import kotlinx.coroutines.flow.Flow

enum class JobKind { CONVERT, EXTRACT_AUDIO, EXTRACT_SUBTITLES, RENDER }

/** What the app remembers about a job after it ends, or after the process that ran it died (step 17). */
enum class JobOutcome { RUNNING, COMPLETED, FAILED, SAVE_FAILED, CANCELLED, INTERRUPTED }

data class JobRecord(
    val id: String,
    val title: String,
    val kind: JobKind,
    /** Where the result is saved (a content URI string). */
    val destination: String,
    val outcome: JobOutcome,
    val startedAtMs: Long,
    val finishedAtMs: Long? = null,
)

/** Durable job history. Pure seam; Room implements it. */
interface JobHistory {
    fun records(): Flow<List<JobRecord>>
    suspend fun save(record: JobRecord)
    suspend fun finish(id: String, outcome: JobOutcome, atMs: Long)
    /** Jobs still marked running that were started before [processStartMs] died with their process. */
    suspend fun markInterrupted(processStartMs: Long, atMs: Long)
    suspend fun clearFinished()
}

object JobRecords {
    fun kindOf(task: ExportTask): JobKind = when (task) {
        is ExportTask.Convert -> JobKind.CONVERT
        is ExportTask.Extract -> JobKind.EXTRACT_AUDIO
        is ExportTask.ExtractSubtitle -> JobKind.EXTRACT_SUBTITLES
        is ExportTask.Render -> JobKind.RENDER
    }

    fun started(job: ExportJob, nowMs: Long) =
        JobRecord(job.id, job.title, kindOf(job.task), job.destination.toString(), JobOutcome.RUNNING, nowMs)

    /** A final status as an outcome; null for a status that is not final. */
    fun outcomeOf(status: JobStatus): JobOutcome? = when (status) {
        JobStatus.Completed -> JobOutcome.COMPLETED
        JobStatus.Cancelled -> JobOutcome.CANCELLED
        is JobStatus.Failed -> when (status.reason) {
            JobFailure.SaveFailed -> JobOutcome.SAVE_FAILED
            JobFailure.Interrupted -> JobOutcome.INTERRUPTED
            else -> JobOutcome.FAILED
        }
        else -> null
    }
}
