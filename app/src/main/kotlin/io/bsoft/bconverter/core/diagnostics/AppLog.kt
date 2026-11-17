package io.bsoft.bconverter.core.diagnostics

/**
 * The app's one logging seam (implementation plan step 16). Pure Kotlin, so domain code may use it. The application installs
 * a sink at start-up (Logcat today); a crash-reporting SDK such as Crashlytics or Sentry plugs in by replacing [sink] and
 * forwarding [nonFatal] calls, with no other code changes.
 */
object AppLog {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun interface Sink { fun log(level: Level, tag: String, message: String, error: Throwable?) }

    @Volatile var sink: Sink = Sink { _, _, _, _ -> }

    fun d(tag: String, message: String) = sink.log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = sink.log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = sink.log(Level.WARN, tag, message, error)

    /** Something went wrong but the app recovered (a best-effort step failed). Reported, never thrown. */
    fun nonFatal(tag: String, message: String, error: Throwable) = sink.log(Level.ERROR, tag, message, error)
}
