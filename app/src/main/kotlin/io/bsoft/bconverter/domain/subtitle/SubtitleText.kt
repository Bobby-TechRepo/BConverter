package io.bsoft.bconverter.domain.subtitle

import java.util.Locale

/** Shared text helpers for the subtitle codecs. */
internal object SubtitleText {
    fun normalize(text: String): String = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')

    /** `[H:]MM:SS(.|,)mmm` pieces to milliseconds; the fraction is read as a decimal fraction ("5" is 500 ms). */
    fun clockMs(hours: String?, minutes: String, seconds: String, fraction: String?): Long {
        val ms = (fraction ?: "").padEnd(3, '0').take(3).toLong()
        return ((hours?.takeIf { it.isNotEmpty() }?.toLong() ?: 0L) * 3600 + minutes.toLong() * 60 + seconds.toLong()) * 1000 + ms
    }

    fun format(ms: Long, millisSeparator: Char, alwaysHours: Boolean = true): String {
        val h = ms / 3_600_000; val m = ms / 60_000 % 60; val s = ms / 1000 % 60; val milli = ms % 1000
        val clock = if (alwaysHours || h > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
        return clock + millisSeparator + String.format(Locale.ROOT, "%03d", milli)
    }

    private val TAG_NAMES = setOf("i", "b", "u")
    private val TAG = Regex("(</?[ibu]>)")

    /** Escapes `&` and `<` everywhere except in the `<i> <b> <u>` markup. */
    fun escapeMarkup(text: String): String = text.split(TAG).let { parts ->
        val tags = TAG.findAll(text).map { it.value }.toList()
        buildString { parts.forEachIndexed { i, p -> append(p.replace("&", "&amp;").replace("<", "&lt;")); if (i < tags.size) append(tags[i]) } }
    }

    /** Removes every tag except `<i> <b> <u>` (with their closing tags) and decodes the common HTML entities. */
    fun stripTagsKeepBasic(text: String): String {
        val kept = Regex("<(/?)([^>]*)>").replace(text) { m ->
            val name = m.groupValues[2].trim().takeWhile { it != ' ' && it != '.' && it != '\t' }.lowercase()
            if (name in TAG_NAMES) "<${m.groupValues[1]}$name>" else ""
        }
        return kept.replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&lrm;", "").replace("&rlm;", "").replace("&amp;", "&")
    }
}

/** The shared "blocks separated by blank lines, each with a `start --> end` line" reader used by SRT and WebVTT. */
internal object CueBlocks {
    private val TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")

    class Raw(val startMs: Long, val endMs: Long, val text: String, val line: Int)

    /** Reads [blocks] (already split); invalid ones add to [warnings] and are skipped. */
    fun read(blocks: List<Pair<Int, List<String>>>, warnings: MutableList<SubtitleWarning>): List<Raw> = buildList {
        for ((startLine, lines) in blocks) {
            val timeIndex = lines.indexOfFirst { TIME.containsMatchIn(it) }
            if (timeIndex < 0 || timeIndex > 1) { warnings += SubtitleWarning(WarningReason.INVALID_BLOCK, startLine); continue }
            val m = TIME.find(lines[timeIndex])!!.groupValues
            val start = SubtitleText.clockMs(m[1], m[2], m[3], m[4])
            val end = SubtitleText.clockMs(m[5], m[6], m[7], m[8])
            val text = lines.drop(timeIndex + 1).joinToString("\n").trimEnd()
            when {
                SubtitleTiming.validate(start, end) != null -> warnings += SubtitleWarning(WarningReason.INVALID_TIMING, startLine + timeIndex)
                text.isBlank() -> warnings += SubtitleWarning(WarningReason.INVALID_BLOCK, startLine)
                else -> add(Raw(start, end, text, startLine))
            }
        }
    }

    /** Splits [text] into blocks of consecutive non-blank lines, remembering each block's first line number. */
    fun split(text: String): List<Pair<Int, List<String>>> {
        val result = mutableListOf<Pair<Int, List<String>>>()
        var current = mutableListOf<String>(); var first = 1
        text.split("\n").forEachIndexed { i, line ->
            if (line.isBlank()) { if (current.isNotEmpty()) { result += first to current; current = mutableListOf() } }
            else { if (current.isEmpty()) first = i + 1; current += line }
        }
        if (current.isNotEmpty()) result += first to current
        return result
    }
}
