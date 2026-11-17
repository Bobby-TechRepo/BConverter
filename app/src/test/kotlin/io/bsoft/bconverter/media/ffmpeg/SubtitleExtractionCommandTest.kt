package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.media.api.SubtitleExtractionRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExtractionCommandTest {
    private val inFile = File("/data/cache/staging/my movie.mkv")
    private val outFile = File("/data/cache/out file.srt")
    private val meta = MediaMetadata(1000, ContainerFormat.MKV, subtitleStreams = listOf(SubtitleStream(3, "eng", "subrip"), SubtitleStream(4, "fra", "ass"), SubtitleStream(5, "eng", "hdmv_pgs_subtitle")))

    private fun build(ordinal: Int, output: SubtitleOutput) = DefaultFfmpegCommandBuilder().buildSubtitleExtractionCommand(
        SubtitleExtractionRequest("job", ResolvedInput(inFile, "my movie.mkv", 1, true), meta, outFile, SubtitleExtractionSettings(ordinal, output)),
    )
    private fun FfmpegCommand.opt(name: String) = output.options.value(name)
    private fun FfmpegOptions.values(n: String) = entries.filter { it.first == n }.mapNotNull { it.second }

    @Test fun paths_are_single_arguments_and_only_the_chosen_subtitle_stream_is_mapped() {
        val args = build(1, SubtitleOutput.SRT).toArguments()
        assertEquals(inFile.absolutePath, args[args.indexOf("-i") + 1]); assertEquals(outFile.absolutePath, args.last())
        assertEquals(listOf("0:s:1"), build(1, SubtitleOutput.SRT).output.options.values("-map"))
        listOf("-vn", "-an", "-dn").forEach { assertTrue(it, build(0, SubtitleOutput.SRT).output.options.has(it)) }
    }

    @Test fun the_same_codec_is_copied_and_others_are_converted() {
        assertEquals("copy", build(0, SubtitleOutput.SRT).opt("-c:s"))
        assertEquals("srt", build(1, SubtitleOutput.SRT).opt("-c:s"))
        assertEquals("ass", build(0, SubtitleOutput.ASS).opt("-c:s"))
        assertEquals("ass", build(1, SubtitleOutput.ASS).opt("-c:s")) // never a packet copy into the ass muxer
        assertEquals("webvtt", build(0, SubtitleOutput.WEBVTT).opt("-c:s"))
    }

    @Test fun image_subtitles_are_always_copied_into_their_container() {
        assertEquals("copy", build(2, SubtitleOutput.SUP).opt("-c:s")); assertEquals("sup", build(2, SubtitleOutput.SUP).opt("-f"))
        assertEquals("copy", build(2, SubtitleOutput.MKS).opt("-c:s")); assertEquals("matroska", build(2, SubtitleOutput.MKS).opt("-f"))
    }

    @Test fun the_container_format_is_forced_so_the_file_name_does_not_matter() {
        assertEquals("srt", build(0, SubtitleOutput.SRT).opt("-f")); assertEquals("webvtt", build(0, SubtitleOutput.WEBVTT).opt("-f"))
        assertEquals("ass", build(1, SubtitleOutput.ASS).opt("-f"))
    }

    @Test fun language_and_title_metadata_are_carried_over() {
        assertEquals("0", build(2, SubtitleOutput.MKS).opt("-map_metadata"))
    }

    @Test fun global_options_overwrite_quietly_and_keep_stats() {
        val g = build(0, SubtitleOutput.SRT).globalOptions
        assertTrue("-y" in g); assertTrue("-hide_banner" in g)
    }
}
