package io.bsoft.bconverter.core.diagnostics

import java.io.File

/**
 * Plain-text report of what the device recorded: app and device lines, the last crash, and the end of the FFmpeg failure
 * log. Pure (files and lines are passed in) so it is unit-tested. Nothing is sent anywhere; the user copies it.
 */
object DiagnosticsReport {
    const val MAX_SECTION_CHARS = 12_000

    fun build(header: String, crashFile: File, ffmpegLog: File): String = buildString {
        appendLine(header)
        appendLine()
        appendLine("== Last crash")
        appendLine(crashFile.takeIf { it.isFile }?.readText()?.takeLast(MAX_SECTION_CHARS)?.trim() ?: "none")
        appendLine()
        appendLine("== FFmpeg failures (most recent last)")
        appendLine(ffmpegLog.takeIf { it.isFile }?.readText()?.takeLast(MAX_SECTION_CHARS)?.trim() ?: "none")
    }
}
