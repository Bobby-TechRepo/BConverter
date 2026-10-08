package io.bsoft.bconverter.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.bsoft.bconverter.core.storage.StagingDirectory
import javax.inject.Inject

@HiltAndroidApp
class BConverterApplication : Application() {
    @Inject lateinit var stagingDirectory: StagingDirectory

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        // Remove abandoned staged copies; recent ones may belong to a running job.
        Thread { stagingDirectory.clearOlderThan(System.currentTimeMillis() - STALE_STAGING_MS) }.start()
    }

    private companion object { const val STALE_STAGING_MS = 24L * 60 * 60 * 1000 }
}
