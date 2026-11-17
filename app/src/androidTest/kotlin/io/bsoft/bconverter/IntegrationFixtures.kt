package io.bsoft.bconverter

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Instrumented tests share one tiny MP4 (mpeg4 + aac, 2 s, 160x120, ~45 KB) shipped as a test asset.
 * It used to be generated with FFmpeg's lavfi sources, which blocks right after the banner on some devices,
 * so the fixture no longer depends on the engine under test. Treat the file as read-only.
 */
object IntegrationFixtures {
    private const val ASSET = "sample-160x120.mp4"

    @Synchronized
    fun sampleMp4(context: Context): File {
        val dir = File(context.cacheDir, "fixtures").apply { mkdirs() }
        val file = File(dir, ASSET)
        if (file.length() > 0) return file
        // The asset lives in the test APK, so it is read through the instrumentation's own context.
        val tmp = File(dir, "$ASSET.part")
        InstrumentationRegistry.getInstrumentation().context.assets.open(ASSET).use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        check(tmp.renameTo(file)) { "could not install the sample video fixture" }
        return file
    }
}
