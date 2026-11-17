package io.bsoft.bconverter.app

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.bsoft.bconverter.core.coroutine.ApplicationScope
import io.bsoft.bconverter.core.diagnostics.AppLog
import io.bsoft.bconverter.domain.usecase.JobHistory
import io.bsoft.bconverter.core.diagnostics.CrashRecorder
import io.bsoft.bconverter.core.diagnostics.LogcatSink
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.database.ProjectDao
import io.bsoft.bconverter.data.storage.AndroidUriGrantCleaner
import io.bsoft.bconverter.data.storage.OverlayImageCleaner
import io.bsoft.bconverter.domain.repository.ExportScheduler
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.feature.export.ExportWorkerFactory
import io.bsoft.bconverter.media.ffmpeg.FfmpegFonts
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class BConverterApplication : Application(), Configuration.Provider {
    @Inject lateinit var stagingDirectory: StagingDirectory
    @Inject lateinit var workerFactory: ExportWorkerFactory
    // Exposed for the instrumented end-to-end test.
    @Inject lateinit var exportRegistry: ExportRegistry
    @Inject lateinit var exportScheduler: ExportScheduler
    @Inject lateinit var projectDao: ProjectDao
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var jobHistory: JobHistory

    /** WorkManager is initialised on demand (default initializer removed in the manifest) so it can use our factory. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        val processStartMs = System.currentTimeMillis()
        super.onCreate() // Hilt injects here
        // Logging and crash capture first, so everything after is covered (step 16).
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppLog.sink = LogcatSink(debuggable)
        CrashRecorder(File(filesDir, DIAGNOSTICS_DIR), ::deviceLine).install()
        // One small font copy on first launch; renders read FfmpegFonts.fontsDir, so it is set before any job can start.
        FfmpegFonts.configure(this)
        // Housekeeping, off the main thread and independent of each other: a failure only leaves work for the next start.
        appScope.launch(Dispatchers.IO) { bestEffort { stagingDirectory.clearOlderThan(System.currentTimeMillis() - STALE_STAGING_MS) } }
        appScope.launch(Dispatchers.IO) {
            bestEffort { OverlayImageCleaner({ projectDao.payloads() }, File(filesDir, "overlay-images"), System::currentTimeMillis).clean() }
        }
        // Jobs still "running" from an earlier process died with it (step 17).
        appScope.launch(Dispatchers.IO) { bestEffort { jobHistory.markInterrupted(processStartMs, System.currentTimeMillis()) } }
        appScope.launch(Dispatchers.IO) { bestEffort { AndroidUriGrantCleaner(contentResolver) { projectDao.payloads() }.clean() } }
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLog.nonFatal(TAG, "Start-up housekeeping failed; retried next start", e) }
    }

    /** One line identifying the build and device, for crash and diagnostics reports. */
    fun deviceLine(): String {
        val version = runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            "${info.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("unknown")
        return "BConverter $version · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · ${android.os.Build.SUPPORTED_ABIS.joinToString()}"
    }

    companion object {
        private const val STALE_STAGING_MS = 24L * 60 * 60 * 1000
        private const val TAG = "App"
        /** Under filesDir: last-crash.txt and ffmpeg-failures.log. */
        const val DIAGNOSTICS_DIR = "diagnostics"
    }
}
