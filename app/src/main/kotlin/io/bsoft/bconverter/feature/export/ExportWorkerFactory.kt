package io.bsoft.bconverter.feature.export

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import javax.inject.Inject

class ExportWorkerFactory @Inject constructor(
    private val executor: ExportJobExecutor,
    private val registry: ExportRegistry,
    private val notifier: ExportNotifier,
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == ExportWorker::class.java.name) ExportWorker(appContext, workerParameters, executor, registry, notifier) else null
}
