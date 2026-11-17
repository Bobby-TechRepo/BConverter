package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.subtitle.SoftSubtitleCodec
import java.io.File

/** A subtitle file to embed as its own track. */
data class SoftSubtitleFile(
    val file: File, val codec: SoftSubtitleCodec,
    val language: String? = null, val title: String? = null, val isDefault: Boolean = false, val isForced: Boolean = false,
)

/** An ASS file to draw into the picture. */
data class BurnedSubtitleFile(val file: File)

/** The subtitle files prepared for one render. [fontsDir] is where libass looks for fonts when burning. */
data class RenderSubtitles(val soft: List<SoftSubtitleFile>, val burned: List<BurnedSubtitleFile>, val fontsDir: String? = null) {
    val isEmpty: Boolean get() = soft.isEmpty() && burned.isEmpty()
    val files: List<File> get() = soft.map { it.file } + burned.map { it.file }

    companion object { val None = RenderSubtitles(emptyList(), emptyList()) }
}

/** The `subtitles` filter with its path escaped for both FFmpeg parsing levels (filter options, then the filter graph). */
object SubtitleFilter {
    fun render(file: File, fontsDir: String?): String =
        "subtitles=filename=" + escape(file.path) + (fontsDir?.let { ":fontsdir=" + escape(it) } ?: "")

    private fun escape(value: String): String {
        val option = value.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:")
        return buildString { option.forEach { c -> if (c in "\\'[],;") append('\\'); append(c) } }
    }
}
