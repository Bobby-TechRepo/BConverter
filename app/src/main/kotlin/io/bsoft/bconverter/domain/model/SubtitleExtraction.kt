package io.bsoft.bconverter.domain.model

enum class SubtitleKind { TEXT, IMAGE, UNSUPPORTED }

/** Maps FFmpeg subtitle codec names to what the app can do with them. */
object SubtitleCodecInfo {
    private val text = setOf(
        "subrip", "srt", "ass", "ssa", "webvtt", "mov_text", "ttml", "text", "subviewer", "subviewer1", "microdvd", "sami",
        "realtext", "jacosub", "mpl2", "pjs", "stl", "vplayer", "hdmv_text_subtitle",
    )
    private val image = setOf("dvd_subtitle", "dvdsub", "dvb_subtitle", "hdmv_pgs_subtitle", "xsub")

    fun kind(codec: String?): SubtitleKind = when (codec?.lowercase()) {
        null -> SubtitleKind.UNSUPPORTED
        in text -> SubtitleKind.TEXT
        in image -> SubtitleKind.IMAGE
        else -> SubtitleKind.UNSUPPORTED
    }
}

/** What an extracted stream is written as. [encoder] null means the stream can only be copied. */
enum class SubtitleOutput(val extension: String, val mimeType: String, val muxer: String, val encoder: String?, val kind: SubtitleKind) {
    SRT("srt", "application/x-subrip", "srt", "srt", SubtitleKind.TEXT),
    ASS("ass", "text/x-ssa", "ass", "ass", SubtitleKind.TEXT),
    WEBVTT("vtt", "text/vtt", "webvtt", "webvtt", SubtitleKind.TEXT),
    SUP("sup", "application/octet-stream", "sup", null, SubtitleKind.IMAGE),
    MKS("mks", "video/x-matroska", "matroska", null, SubtitleKind.IMAGE),
}

/** [streamOrdinal] is the position among the source's subtitle streams (0 = first). */
data class SubtitleExtractionSettings(val streamOrdinal: Int, val output: SubtitleOutput)
