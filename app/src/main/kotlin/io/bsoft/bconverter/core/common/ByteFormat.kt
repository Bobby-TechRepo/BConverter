package io.bsoft.bconverter.core.common

import java.util.Locale

object ByteFormat {
    private val units = listOf("KB", "MB", "GB", "TB")

    /** "1.5 KB" style, binary steps, always a '.' decimal point. */
    fun format(bytes: Long): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
