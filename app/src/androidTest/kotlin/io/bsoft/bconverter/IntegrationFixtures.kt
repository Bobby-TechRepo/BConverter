package io.bsoft.bconverter

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Instrumented tests share one tiny generated MP4 (mpeg4 + aac, 2 s, 160x120) instead of re-encoding it per test.
 * Encoding on a slow emulator is the heaviest thing these suites do, so it happens once per process.
 * Treat the file as read-only.
 */
object IntegrationFixtures {
    @Synchronized
    fun sampleMp4(context: Context): File {
        val dir = File(context.cacheDir, "fixtures").apply { mkdirs() }
        val file = File(dir, "sample-160x120.mp4")
        if (file.length() > 0) return file
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=160x120:rate=10",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:v", "mpeg4", "-c:a", "aac", "-shortest", file.absolutePath,
            ),
        )
        check(ReturnCode.isSuccess(session.returnCode)) { "fixture generation failed: ${session.output}" }
        FFmpegKitConfig.clearSessions() // do not keep the session history in memory
        return file
    }
}
