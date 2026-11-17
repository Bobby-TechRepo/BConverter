package io.bsoft.bconverter.core.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last crash to `files/diagnostics/last-crash.txt` before the process dies, then hands the crash on to the
 * previous handler (the system's, or a crash SDK's), so nothing else changes. Stays on the device; the user can copy it
 * from About. Best effort: a failure to write is ignored.
 */
class CrashRecorder(private val dir: File, private val describe: () -> String) {
    val file: File get() = File(dir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(threadName: String, error: Throwable) {
        runCatching {
            dir.mkdirs()
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date())
            file.writeText("Crash at $stamp on thread $threadName\n${describe()}\n\n$trace")
        }
    }

    private companion object { const val FILE_NAME = "last-crash.txt" }
}
