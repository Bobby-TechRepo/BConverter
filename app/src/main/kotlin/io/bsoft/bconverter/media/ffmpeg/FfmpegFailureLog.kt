package io.bsoft.bconverter.media.ffmpeg

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps what FFmpeg said before a job failed, so field failures can be diagnosed: written to Logcat and appended to
 * `files/diagnostics/ffmpeg-failures.log`, which is capped at [maxBytes] (oldest entries dropped). Stays on the device;
 * nothing is uploaded. Best effort: a write failure is ignored.
 */
class FfmpegFailureLog(private val dir: File, private val maxBytes: Int = DEFAULT_MAX_BYTES) {
    val file: File get() = File(dir, FILE_NAME)

    @Synchronized fun record(jobId: String, exitCode: Int?, logTail: String) {
        Log.w(TAG, "FFmpeg job $jobId failed (exit $exitCode):\n${logTail.takeLast(LOGCAT_CHARS)}")
        runCatching {
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
            val entry = "=== $stamp job=$jobId exit=$exitCode\n$logTail\n\n"
            val combined = (if (file.isFile) file.readText() else "") + entry
            file.writeText(if (combined.length > maxBytes) combined.takeLast(maxBytes) else combined)
        }
    }

    private companion object {
        const val TAG = "BConverterFFmpeg"
        const val FILE_NAME = "ffmpeg-failures.log"
        const val DEFAULT_MAX_BYTES = 64 * 1024
        const val LOGCAT_CHARS = 3_000
    }
}
