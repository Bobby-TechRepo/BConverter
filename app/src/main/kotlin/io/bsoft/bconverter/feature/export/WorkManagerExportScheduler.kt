package io.bsoft.bconverter.feature.export

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.bsoft.bconverter.domain.repository.ExportScheduler

class WorkManagerExportScheduler(private val context: Context) : ExportScheduler {
    // Lazy: WorkManager's configuration needs the Hilt-injected worker factory, which is not ready during Application injection.
    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun enqueue(jobId: String) {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(workDataOf(ExportWorker.KEY_JOB_ID to jobId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(jobId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(jobId: String) { workManager.cancelUniqueWork(workName(jobId)) }

    private fun workName(jobId: String) = "export-$jobId"

    private companion object { const val TAG = "export" }
}
