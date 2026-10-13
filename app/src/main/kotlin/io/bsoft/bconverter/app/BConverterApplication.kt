package io.bsoft.bconverter.app

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.repository.ExportScheduler
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.feature.export.ExportWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class BConverterApplication : Application(), Configuration.Provider {
    @Inject lateinit var stagingDirectory: StagingDirectory
    @Inject lateinit var workerFactory: ExportWorkerFactory
    // Exposed for the instrumented end-to-end test.
    @Inject lateinit var exportRegistry: ExportRegistry
    @Inject lateinit var exportScheduler: ExportScheduler

    /** WorkManager is initialised on demand (default initializer removed in the manifest) so it can use our factory. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        // Remove abandoned staged copies; recent ones may belong to a running job.
        Thread { stagingDirectory.clearOlderThan(System.currentTimeMillis() - STALE_STAGING_MS) }.start()
    }

    private companion object { const val STALE_STAGING_MS = 24L * 60 * 60 * 1000 }
}
