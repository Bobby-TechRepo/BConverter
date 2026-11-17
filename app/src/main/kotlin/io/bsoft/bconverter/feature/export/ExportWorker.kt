package io.bsoft.bconverter.feature.export

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Runs an export job as foreground work so it keeps going while the app is in the background (spec §35). */
class ExportWorker(
    context: Context,
    params: WorkerParameters,
    private val executor: ExportJobExecutor,
    private val registry: ExportRegistry,
    private val notifier: ExportNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        // After a process restart the in-memory job is gone and cannot be resumed.
        val job = registry.job(jobId) ?: return Result.failure()
        showForeground(job.title, null)
        return coroutineScope {
            val progress = launch {
                var lastPercent = -1
                registry.status(jobId)?.collect { status ->
                    if (status is JobStatus.Running) {
                        val percent = (status.progress * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            setProgress(workDataOf(KEY_PROGRESS to status.progress))
                            showForeground(job.title, status.progress)
                        }
                    }
                }
            }
            val status = try { executor.execute(jobId) } finally { progress.cancel() }
            notifier.showResult(job.title, status)
            if (status is JobStatus.Completed) Result.success() else Result.failure()
        }
    }

    /** Needed by WorkManager for expedited work on API < 31. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_JOB_ID)?.let { registry.job(it)?.title }.orEmpty()
        return notifier.foregroundInfo(id, title, null)
    }

    private suspend fun showForeground(title: String, progress: Float?) {
        try {
            setForeground(notifier.foregroundInfo(id, title, progress))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The system may refuse a foreground service right now; keep working without the notification.
            io.bsoft.bconverter.core.diagnostics.AppLog.nonFatal("Export", "Foreground notification refused", e)
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_PROGRESS = "progress"
    }
}
