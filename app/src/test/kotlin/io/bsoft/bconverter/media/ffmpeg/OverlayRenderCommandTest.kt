package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayLayer
import io.bsoft.bconverter.domain.overlay.TextOverlay
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

class OverlayRenderCommandTest {
    private val out = File("/cache/out.mp4")
    private val project = TL.project(TL.timeline(TL.video("v1", 4000)))
    private fun layer(id: String, o: TextOverlay = TextOverlay(id, "Hi", 500, 2500)) = OverlayLayer(o, File("/cache/ol-$id.png"), 200, 80)
    private fun build(vararg l: OverlayLayer, subs: RenderSubtitles = RenderSubtitles.None) =
        DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out, subs, l.toList()))
    private fun graph(c: FfmpegCommand) = c.output.options.value("-filter_complex")!!

    @Test fun without_overlays_the_command_has_no_overlay_filters() {
        val c = build()
        assertFalse(graph(c).contains("overlay=")); assertEquals(1, c.inputs.size)
    }
    @Test fun each_overlay_is_a_looped_picture_input_after_the_clips() {
        val c = build(layer("a"), layer("b"))
        assertEquals(listOf("/cache/ol-a.png", "/cache/ol-b.png").map { File(it).absolutePath }, c.inputs.drop(1).map { it.path })
        c.inputs.drop(1).forEach {
            assertEquals("1", it.options.value("-loop")); assertEquals("4", it.options.value("-t")); assertTrue(it.options.value("-framerate") != null)
        }
    }
    @Test fun the_last_video_label_is_mapped() {
        val c = build(layer("a"), layer("b"))
        assertEquals("[ovv2]", c.output.options.values("-map").first())
        assertTrue(graph(c).contains("overlay="))
    }
    @Test fun picture_inputs_come_after_soft_subtitle_files_and_are_indexed_accordingly() {
        val soft = SoftSubtitleFile(File("/cache/a.srt"), SoftSubtitleCodec.SRT, null, null, false, false)
        val c = build(layer("a"), subs = RenderSubtitles(listOf(soft), emptyList()))
        assertEquals(listOf("/cache/a.srt", "/cache/ol-a.png").map { File(it).absolutePath }, c.inputs.drop(1).map { it.path })
        assertTrue(graph(c), graph(c).contains("[2:v]format=rgba[ovp1]"))
        assertTrue(c.output.options.values("-map").contains("1:0"))
    }
    @Test fun burned_subtitles_are_drawn_over_the_text_overlays() {
        val c = build(layer("a"), subs = RenderSubtitles(emptyList(), listOf(BurnedSubtitleFile(File("/cache/a.ass")))))
        val g = graph(c)
        assertTrue(g, g.indexOf("overlay=") < g.indexOf("subtitles="))
        assertTrue(g, g.contains("[ovv1]subtitles="))
    }
    @Test fun animations_reach_the_command() {
        val o = TextOverlay("a", "Hi", 500, 2500, animIn = OverlayAnimation(AnimationKind.FADE, 300))
        assertTrue(graph(build(layer("a", o))).contains("fade=t=in:st=0.5:d=0.3:alpha=1"))
    }
}
