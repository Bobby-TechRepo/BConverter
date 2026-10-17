package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.render.FilterGraphBuilder
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderCommandTest {
    private val builder = DefaultFfmpegCommandBuilder()
    private val output = RenderOutput(File("/out/final file.mp4"))
    // a: 10 s at 1x; b: 2..8 s of its source (6 s) at 2x = 3 s on the timeline; total 13 s
    private fun timeline() = TL.withAudio(
        TL.timeline(TL.video("a", 10_000), TL.video("b", 6_000, speed = 2f, sourceStart = 2_000, sourceDuration = 10_000)),
        listOf(TL.audio("m", 4_000, start = 1_500, sourceStart = 500, sourceDuration = 5_000)),
    )
    private fun build(p: VideoProject = TL.project(timeline())) = builder.buildRenderCommand(p, output)
    private fun FfmpegCommand.opt(name: String) = output.options.value(name)
    private fun expectedGraph(p: VideoProject) = FilterGraphBuilder.build((RenderPlanner.plan(p) as RenderPlanResult.Success).plan).render()

    // ---- inputs ----
    @Test fun every_clip_is_an_input_with_its_own_seek_and_duration() {
        val inputs = build().inputs
        assertEquals(listOf("/tmp/a", "/tmp/b", "/tmp/m").map { File(it).absolutePath }, inputs.map { it.path })
        assertNull(inputs[0].options.value("-ss")); assertEquals("10", inputs[0].options.value("-t"))
        assertEquals("2", inputs[1].options.value("-ss")); assertEquals("6", inputs[1].options.value("-t"))
        assertEquals("0.5", inputs[2].options.value("-ss")); assertEquals("4", inputs[2].options.value("-t"))
    }

    // ---- graph, mapping, duration ----
    @Test fun the_filter_graph_comes_from_the_plan() = assertEquals(expectedGraph(TL.project(timeline())), build().opt("-filter_complex"))

    @Test fun video_and_audio_outputs_are_mapped_and_the_length_is_the_video_length() {
        val cmd = build()
        assertEquals(listOf("[vout]", "[aout]"), cmd.output.options.values("-map")); assertEquals("13", cmd.opt("-t"))
    }

    @Test fun paths_are_single_arguments_and_the_output_is_last() {
        val args = build().toArguments(); assertEquals(File("/out/final file.mp4").absolutePath, args.last()); assertTrue("-y" in args)
    }

    // ---- encoding ----
    @Test fun video_encoding_options_come_from_the_export_settings_without_scaling_or_rate_options() {
        val cmd = build()
        assertEquals("libx264", cmd.opt("-c:v")); assertEquals("23", cmd.opt("-crf")); assertEquals("yuv420p", cmd.opt("-pix_fmt"))
        assertFalse(cmd.output.options.has("-vf")); assertFalse(cmd.output.options.has("-r"))
    }

    @Test fun the_resolution_and_frame_rate_live_in_the_graph_not_in_output_options() {
        val p = TL.project(timeline(), TL.exportSettings(VideoSettings(VideoCodec.H264, VideoQuality.Crf(23), Resolution(1280, 720), 24.0)))
        val cmd = build(p)
        assertTrue(cmd.opt("-filter_complex")!!.contains("scale=1280:720")); assertTrue(cmd.opt("-filter_complex")!!.contains("fps=24"))
        assertFalse(cmd.output.options.has("-r"))
    }

    @Test fun audio_encoding_and_container_options() {
        val cmd = build()
        assertEquals("aac", cmd.opt("-c:a")); assertEquals("128k", cmd.opt("-b:a")); assertEquals("mp4", cmd.opt("-f")); assertEquals("+faststart", cmd.opt("-movflags"))
    }

    @Test fun a_project_without_audio_settings_is_rendered_silent() {
        val cmd = build(TL.project(timeline(), TL.exportSettings(audio = null)))
        assertEquals(listOf("[vout]"), cmd.output.options.values("-map")); assertTrue(cmd.output.options.has("-an")); assertFalse(cmd.output.options.has("-c:a"))
    }

    @Test fun a_project_with_no_audio_sources_is_rendered_silent_even_with_audio_settings() {
        val cmd = build(TL.project(TL.timeline(TL.video("a")), metadata = { TL.meta(audio = false) }))
        assertEquals(listOf("[vout]"), cmd.output.options.values("-map")); assertTrue(cmd.output.options.has("-an"))
    }

    @Test fun audio_settings_are_applied_to_the_encoder() {
        val cmd = build(TL.project(timeline(), TL.exportSettings(audio = AudioSettings(AudioCodec.AAC, 96, 44_100, 1))))
        assertEquals("96k", cmd.opt("-b:a")); assertEquals("44100", cmd.opt("-ar")); assertEquals("1", cmd.opt("-ac"))
    }

    @Test(expected = IllegalArgumentException::class) fun a_project_that_cannot_be_planned_is_a_programming_error() {
        build(TL.project(Timeline()))
    }
}
