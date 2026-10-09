package io.bsoft.bconverter.media.ffmpeg

enum class FfmpegCodecKind { VIDEO, AUDIO, SUBTITLE }

data class FfmpegCodecEntry(val name: String, val kind: FfmpegCodecKind, val experimental: Boolean)

/** Parses the output of `ffmpeg -hide_banner -encoders` / `-decoders`. */
object FfmpegCodecListParser {
    private val LINE = Regex("""^\s*([VAS])([.A-Z]{5})\s+(\S+)""")

    fun parse(output: String): List<FfmpegCodecEntry> {
        val lines = output.lines()
        val separator = lines.indexOfFirst { it.trim().startsWith("------") }
        if (separator < 0) return emptyList()
        return lines.drop(separator + 1).mapNotNull { line ->
            val m = LINE.find(line) ?: return@mapNotNull null
            val kind = when (m.groupValues[1]) {
                "V" -> FfmpegCodecKind.VIDEO
                "A" -> FfmpegCodecKind.AUDIO
                else -> FfmpegCodecKind.SUBTITLE
            }
            FfmpegCodecEntry(m.groupValues[3], kind, experimental = m.groupValues[2][2] == 'X')
        }
    }
}
