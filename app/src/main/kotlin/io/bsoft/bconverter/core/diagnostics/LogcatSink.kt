package io.bsoft.bconverter.core.diagnostics

import android.util.Log

/** [AppLog] → Logcat. Debug lines are dropped in release builds. */
class LogcatSink(private val debug: Boolean) : AppLog.Sink {
    override fun log(level: AppLog.Level, tag: String, message: String, error: Throwable?) {
        val t = "BC/$tag".take(23)
        when (level) {
            AppLog.Level.DEBUG -> if (debug) Log.d(t, message, error)
            AppLog.Level.INFO -> Log.i(t, message, error)
            AppLog.Level.WARN -> Log.w(t, message, error)
            AppLog.Level.ERROR -> Log.e(t, message, error)
        }
    }
}
