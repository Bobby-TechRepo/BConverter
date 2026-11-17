package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleKind
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream

/** Optional OCR for image subtitles. No engine ships yet, so [UnavailableOcr] is the default. */
interface SubtitleOcr { val isAvailable: Boolean }
object UnavailableOcr : SubtitleOcr { override val isAvailable = false }

/** Decides which outputs a subtitle stream can be extracted to. */
class SubtitleExtractionPlanner(private val ocr: SubtitleOcr = UnavailableOcr) {

    fun outputsFor(stream: SubtitleStream): List<SubtitleOutput> = when (stream.kind) {
        SubtitleKind.TEXT -> listOf(SubtitleOutput.SRT, SubtitleOutput.ASS, SubtitleOutput.WEBVTT)
        SubtitleKind.IMAGE -> when (stream.codec?.lowercase()) {
            "hdmv_pgs_subtitle" -> listOf(SubtitleOutput.SUP, SubtitleOutput.MKS)
            else -> listOf(SubtitleOutput.MKS)
        } + if (ocr.isAvailable) listOf(SubtitleOutput.SRT) else emptyList()
        SubtitleKind.UNSUPPORTED -> emptyList()
    }

    fun needsOcr(stream: SubtitleStream) = stream.kind == SubtitleKind.IMAGE

    fun defaultOutput(stream: SubtitleStream): SubtitleOutput? = when (stream.codec?.lowercase()) {
        "ass", "ssa" -> SubtitleOutput.ASS
        "webvtt" -> SubtitleOutput.WEBVTT
        else -> outputsFor(stream).firstOrNull()
    }

    fun isCopy(stream: SubtitleStream, output: SubtitleOutput): Boolean = when (output) {
        SubtitleOutput.SRT -> stream.codec?.lowercase() in setOf("subrip", "srt")
        SubtitleOutput.ASS -> stream.codec?.lowercase() == "ass"
        SubtitleOutput.WEBVTT -> stream.codec?.lowercase() == "webvtt"
        SubtitleOutput.SUP -> stream.codec?.lowercase() == "hdmv_pgs_subtitle"
        SubtitleOutput.MKS -> stream.kind == SubtitleKind.IMAGE
    }

    fun losesStyling(stream: SubtitleStream, output: SubtitleOutput): Boolean =
        stream.codec?.lowercase() in setOf("ass", "ssa") && (output == SubtitleOutput.SRT || output == SubtitleOutput.WEBVTT)

    fun settingsFor(metadata: MediaMetadata, ordinal: Int, output: SubtitleOutput): SubtitleExtractionSettings? {
        val stream = metadata.subtitleStreams.getOrNull(ordinal) ?: return null
        return if (output in outputsFor(stream)) SubtitleExtractionSettings(ordinal, output) else null
    }
}
