package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.render.BurnedSubtitleFile
import io.bsoft.bconverter.domain.render.RenderSubtitles
import io.bsoft.bconverter.domain.render.SoftSubtitleFile
import io.bsoft.bconverter.domain.subtitle.SoftSubtitleCodec
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleRenderCommandTest {
    private val out = File("/cache/out.mkv")
    private fun project(container: ContainerFormat = ContainerFormat.MKV) =
        TL.project(TL.timeline(TL.video("v1", 4000)), export = TL.exportSettings().copy(container = container))
    private fun build(subs: RenderSubtitles, container: ContainerFormat = ContainerFormat.MKV) =
        DefaultFfmpegCommandBuilder().buildRenderCommand(project(container), RenderOutput(out, subs))
    private fun soft(name: String, codec: SoftSubtitleCodec = SoftSubtitleCodec.SRT, language: String? = null, title: String? = null, d: Boolean = false, f: Boolean = false) =
        SoftSubtitleFile(File("/cache/$name"), codec, language, title, d, f)

    @Test fun without_subtitles_the_command_is_unchanged() {
        val args = build(RenderSubtitles.None).toArguments()
        assertFalse(args.contains("-c:s")); assertFalse(args.any { it.contains("subtitles=") }); assertEquals(1, build(RenderSubtitles.None).inputs.size)
    }

    @Test fun soft_tracks_are_extra_inputs_mapped_in_order_with_their_codec() {
        val c = build(RenderSubtitles(listOf(soft("a.srt", SoftSubtitleCodec.SRT), soft("b.ass", SoftSubtitleCodec.ASS)), emptyList()))
        assertEquals(listOf("/cache/a.srt", "/cache/b.ass").map { File(it).absolutePath }, c.inputs.drop(1).map { it.path })
        val o = c.output.options
        assertTrue(o.values("-map").containsAll(listOf("1:0", "2:0")))
        assertEquals("srt", o.value("-c:s:0")); assertEquals("ass", o.value("-c:s:1"))
    }

    @Test fun language_title_and_flags_are_written_per_track() {
        val o = build(RenderSubtitles(listOf(soft("a.srt", language = "eng", title = "English", d = true), soft("b.srt", language = "fra", f = true), soft("c.srt")), emptyList())).output.options
        assertEquals("eng", o.value("-metadata:s:s:0")?.removePrefix("language=")); assertTrue(o.values("-metadata:s:s:0").contains("title=English"))
        assertEquals("default", o.value("-disposition:s:0")); assertEquals("forced", o.value("-disposition:s:1")); assertEquals("0", o.value("-disposition:s:2"))
    }

    @Test fun mp4_uses_mov_text() =
        assertEquals("mov_text", build(RenderSubtitles(listOf(soft("a.srt", SoftSubtitleCodec.MOV_TEXT)), emptyList()), ContainerFormat.MP4).output.options.value("-c:s:0"))

    @Test fun burned_tracks_are_chained_after_the_video_in_the_filter_graph() {
        val c = build(RenderSubtitles(emptyList(), listOf(BurnedSubtitleFile(File("/cache/a.ass")), BurnedSubtitleFile(File("/cache/b.ass"))), fontsDir = "/system/fonts"))
        val graph = c.output.options.value("-filter_complex")!!
        assertTrue(graph, graph.contains("a.ass:fontsdir=/system/fonts")); assertTrue(graph.contains("subtitles=filename=") && graph.contains("b.ass"))
        assertTrue(graph.indexOf("a.ass") < graph.indexOf("b.ass"))
        val mapped = c.output.options.values("-map").first()
        assertTrue(mapped, mapped.startsWith("[") && mapped != "[v]") // the last burned label, not the raw video
        assertEquals(1, c.inputs.size) // burned files are filter options, not inputs
    }

    @Test fun burned_paths_are_escaped_for_the_filter_parser() {
        val c = build(RenderSubtitles(emptyList(), listOf(BurnedSubtitleFile(File("/cache/it's: a [file],x.ass")))))
        val graph = c.output.options.value("-filter_complex")!!
        assertTrue(graph, graph.contains("""it\\\'s\\: a \[file\]\,x.ass"""))
    }

    @Test fun soft_and_burned_can_be_combined_without_overlap() {
        val c = build(RenderSubtitles(listOf(soft("s.srt")), listOf(BurnedSubtitleFile(File("/cache/b.ass")))))
        assertEquals(2, c.inputs.size); assertTrue(c.output.options.value("-filter_complex")!!.contains("b.ass")); assertEquals("srt", c.output.options.value("-c:s:0"))
    }

    @Test fun the_output_is_still_cut_to_the_video_length() = assertTrue(build(RenderSubtitles(listOf(soft("a.srt")), emptyList())).output.options.has("-t"))
}
