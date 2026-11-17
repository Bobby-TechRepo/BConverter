package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.ToneMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClipFilterGraphTest {
    private val canvas = Canvas(1280, 720, 30.0)
    private fun plan(vararg v: VideoSegment) =
        RenderPlan(canvas, v.indices.map { RenderInput(File("/x/$it"), 0, 10_000) }, v.toList(), null, emptyList(), null, 10_000)
    private fun chain(p: RenderPlan, out: String) = FilterGraphBuilder.build(p).chains.single { out in it.outputLabels }.filters

    @Test fun an_unfiltered_clip_is_unchanged() {
        assertFalse(chain(plan(VideoSegment(0, 1f)), "v0").any { it.startsWith("eq") })
        assertEquals("scale=1280:720:force_original_aspect_ratio=decrease", chain(plan(VideoSegment(0, 1f)), "v0")[0])
    }
    @Test fun the_filter_runs_after_scaling_and_before_padding_so_the_bars_stay_black() {
        val f = chain(plan(VideoSegment(0, 1f, filter = ClipFilter(brightness = 0.5f, tone = ToneMode.BLACK_WHITE))), "v0")
        assertEquals(
            listOf("scale=1280:720:force_original_aspect_ratio=decrease", "lutyuv=y='clip(val/maxval+0.15,0,1)*maxval'", "hue=s=0",
                "pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=black", "setsar=1", "setpts=PTS-STARTPTS", "fps=30", "format=yuv420p"),
            f,
        )
    }
    @Test fun each_clip_gets_its_own_filter() {
        val p = plan(VideoSegment(0, 1f, filter = ClipFilter(invert = true)), VideoSegment(1, 1f))
        assertEquals(true, chain(p, "v0").contains("negate")); assertEquals(false, chain(p, "v1").contains("negate"))
    }
    @Test fun blur_follows_the_canvas_height() {
        val f = chain(plan(VideoSegment(0, 1f, filter = ClipFilter(blur = 1f))), "v0")
        assertEquals(true, f.contains("gblur=sigma=8.64"))
    }

    @Test fun a_zoom_runs_on_the_turned_picture_before_it_is_fitted_to_the_canvas() {
        val k = io.bsoft.bconverter.domain.transform.KenBurns(io.bsoft.bconverter.domain.transform.KenBurnsMode.ZOOM_IN, 1.5f)
        val f = chain(plan(VideoSegment(0, 1f, sourceWidth = 1920, sourceHeight = 1080, kenBurns = k, sourceMs = 10_000)), "v0")
        assertEquals("setpts=PTS-STARTPTS", f[0])
        assertEquals("fps=30", f[1])
        assertEquals(true, f[3].startsWith("zoompan=z="))
        assertEquals(true, f[3].contains(":s=1920x1080:fps=30"))
        assertEquals("scale=1280:720:force_original_aspect_ratio=decrease", f[4])
    }
    @Test fun a_freeze_frame_or_a_clip_of_unknown_size_gets_no_zoom() {
        val k = io.bsoft.bconverter.domain.transform.KenBurns(io.bsoft.bconverter.domain.transform.KenBurnsMode.ZOOM_IN)
        assertEquals(false, chain(plan(VideoSegment(0, 1f, kenBurns = k, sourceMs = 5_000)), "v0").any { it.startsWith("zoompan") })
        assertEquals(false, chain(plan(VideoSegment(0, 1f, freeze = true, sourceWidth = 640, sourceHeight = 360, kenBurns = k, sourceMs = 5_000, durationMs = 2_000)), "v0").any { it.startsWith("zoompan") })
    }
}
