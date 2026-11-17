package io.bsoft.bconverter.core.diagnostics

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsTest {
    @get:Rule val tmp = TemporaryFolder()
    @After fun resetSink() { AppLog.sink = AppLog.Sink { _, _, _, _ -> } }

    @Test fun the_report_says_none_when_nothing_was_recorded() {
        val r = DiagnosticsReport.build("BConverter 0.1.0", File(tmp.root, "no-crash"), File(tmp.root, "no-log"))
        assertTrue(r.startsWith("BConverter 0.1.0"))
        assertEquals(2, Regex("(?m)^none$").findAll(r).count())
    }

    @Test fun a_recorded_crash_appears_in_the_report_with_the_device_line() {
        val dir = File(tmp.root, "diagnostics")
        val recorder = CrashRecorder(dir) { "Pixel 9 · Android 16" }
        recorder.record("main", IllegalStateException("boom"))
        val r = DiagnosticsReport.build("header", recorder.file, File(dir, "ffmpeg-failures.log"))
        assertTrue(r.contains("Pixel 9 · Android 16")); assertTrue(r.contains("IllegalStateException: boom"))
    }

    @Test fun long_logs_are_cut_to_their_most_recent_part() {
        val log = File(tmp.root, "ffmpeg.log").apply { writeText("old\n" + "x".repeat(DiagnosticsReport.MAX_SECTION_CHARS) + "\nnewest failure") }
        val r = DiagnosticsReport.build("h", File(tmp.root, "none"), log)
        assertTrue(r.contains("newest failure")); assertTrue(!r.contains("old\n"))
    }

    @Test fun non_fatal_errors_reach_the_installed_sink() {
        val seen = mutableListOf<Pair<AppLog.Level, String>>()
        AppLog.sink = AppLog.Sink { level, tag, _, _ -> seen += level to tag }
        AppLog.nonFatal("Export", "publish failed", RuntimeException())
        assertEquals(listOf(AppLog.Level.ERROR to "Export"), seen)
    }
}
