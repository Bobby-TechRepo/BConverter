package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.canvas.CanvasAspect
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CanvasRenderTest {
    private fun plan(canvas: CanvasSettings, vararg clips: VideoClip) =
        (RenderPlanner.plan(TL.project(TL.timeline(*clips)).copy(canvas = canvas)) as RenderPlanResult.Success).plan
    private fun chain(p: RenderPlan, label: String) = FilterGraphBuilder.build(p).chains.first { it.outputLabels == listOf(label) }
    private val clip get() = TL.video("a", 4_000)

    @Test fun the_default_canvas_pads_with_black_exactly_as_before() {
        val f = chain(plan(CanvasSettings(), clip), "v0").filters
        assertTrue("$f", "pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=black" in f)
    }
    @Test fun the_shape_sets_the_canvas_size() {
        val c = plan(CanvasSettings(aspect = CanvasAspect.TALL), clip).canvas
        assertEquals(1080, c.width); assertEquals(1920, c.height)
    }
    @Test fun a_fixed_export_size_still_wins() {
        val project = TL.project(TL.timeline(clip))
        val sized = project.copy(canvas = CanvasSettings(aspect = CanvasAspect.TALL), export = project.export.copy(video = project.export.video.copy(resolution = io.bsoft.bconverter.domain.model.Resolution(720, 1280))))
        val c = (RenderPlanner.plan(sized) as RenderPlanResult.Success).plan.canvas
        assertEquals(720, c.width); assertEquals(1280, c.height)
    }
    @Test fun a_picture_in_a_taller_canvas_is_scaled_to_fit_and_padded() {
        val f = chain(plan(CanvasSettings(aspect = CanvasAspect.TALL), clip), "v0").filters
        assertTrue("$f", "scale=1080:1920:force_original_aspect_ratio=decrease" in f)
        assertTrue("$f", "pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=black" in f)
    }
    @Test fun a_colour_fills_the_bars() {
        val f = chain(plan(CanvasSettings(aspect = CanvasAspect.TALL, background = CanvasBackground.Color(0x1A2B3C)), clip), "v0").filters
        assertTrue("$f", "pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x1A2B3C" in f)
    }
    @Test fun fill_covers_the_canvas_and_crops_without_bars() {
        val f = chain(plan(CanvasSettings(aspect = CanvasAspect.TALL, fit = CanvasFit.FILL), clip), "v0").filters
        assertTrue("$f", "scale=1080:1920:force_original_aspect_ratio=increase" in f)
        assertTrue("$f", "crop=1080:1920" in f)
        assertTrue("$f", f.none { it.startsWith("pad=") })
    }
    @Test fun fill_ignores_the_blur_background() {
        val g = FilterGraphBuilder.build(plan(CanvasSettings(aspect = CanvasAspect.TALL, fit = CanvasFit.FILL, background = CanvasBackground.Blur), clip))
        assertTrue(g.chains.none { c -> c.filters.any { it.startsWith("gblur") || it == "split=2" } })
    }
    @Test fun a_blur_background_splits_the_picture_and_lays_it_over_a_blurred_copy() {
        val g = FilterGraphBuilder.build(plan(CanvasSettings(aspect = CanvasAspect.TALL, background = CanvasBackground.Blur), clip))
        val rendered = g.render()
        assertTrue(rendered, "[0:v]" in rendered)
        val head = g.chains.first { "f0" in it.outputLabels }
        assertEquals(listOf("0:v"), head.inputLabels); assertEquals("split=2", head.filters.last()); assertEquals(listOf("f0", "g0"), head.outputLabels)
        val back = g.chains.first { it.outputLabels == listOf("b0") }
        assertEquals(listOf("g0"), back.inputLabels)
        assertEquals("scale=134:240:force_original_aspect_ratio=increase", back.filters[0]); assertEquals("crop=134:240", back.filters[1])
        assertTrue(back.filters.any { it.startsWith("gblur=sigma=") }); assertEquals("scale=1080:1920:flags=bilinear", back.filters.last())
        val top = g.chains.first { it.outputLabels == listOf("v0") }
        assertEquals(listOf("b0", "f0"), top.inputLabels); assertEquals("overlay=(W-w)/2:(H-h)/2", top.filters.first())
        assertTrue(top.filters.none { it.startsWith("pad=") })
    }
    @Test fun the_look_is_applied_before_the_split_so_the_copy_has_it_too() {
        val clipWithLook = clip.copy(filter = io.bsoft.bconverter.domain.filter.ClipFilter.None.copy(brightness = 0.2f))
        val g = FilterGraphBuilder.build(plan(CanvasSettings(aspect = CanvasAspect.TALL, background = CanvasBackground.Blur), clipWithLook))
        val head = g.chains.first { "f0" in it.outputLabels }.filters
        assertTrue("$head", head.indexOfFirst { it.startsWith("lutyuv") } in 0 until head.indexOf("split=2"))
    }
    @Test fun the_retime_and_format_come_after_the_overlay() {
        val g = FilterGraphBuilder.build(plan(CanvasSettings(aspect = CanvasAspect.TALL, background = CanvasBackground.Blur), clip.copy(playbackSpeed = 2f)))
        val top = g.chains.first { it.outputLabels == listOf("v0") }.filters
        assertTrue("$top", top.indexOf("overlay=(W-w)/2:(H-h)/2") < top.indexOfFirst { it.startsWith("setpts=(PTS") })
        assertEquals("format=yuv420p", top.last())
    }
    @Test fun a_picture_that_already_has_the_canvas_shape_needs_no_blur() {
        val g = FilterGraphBuilder.build(plan(CanvasSettings(background = CanvasBackground.Blur), clip))
        assertTrue(g.chains.none { c -> c.filters.any { it.startsWith("gblur") } })
        assertTrue(g.chains.first { it.outputLabels == listOf("v0") }.filters.any { it.startsWith("pad=") })
    }
    @Test fun every_clip_gets_its_own_blur_chains_and_they_still_concatenate() {
        val g = FilterGraphBuilder.build(plan(CanvasSettings(aspect = CanvasAspect.TALL, background = CanvasBackground.Blur), clip, TL.video("b", 4_000)))
        assertEquals(2, g.chains.count { c -> c.filters.any { it.startsWith("gblur") } })
        val join = g.chains.first { it.outputLabels == listOf("vout") }
        assertEquals(listOf("v0", "v1"), join.inputLabels)
    }
    @Test fun a_freeze_frame_keeps_its_trim_before_the_split() {
        val freeze = clip.copy(freezeMs = 2_000)
        val g = FilterGraphBuilder.build(plan(CanvasSettings(aspect = CanvasAspect.TALL, background = CanvasBackground.Blur), freeze))
        val head = g.chains.first { "f0" in it.outputLabels }.filters
        assertEquals("trim=end_frame=1", head.first())
    }
    @Test fun the_plan_carries_the_fit_and_background() {
        val p = plan(CanvasSettings(fit = CanvasFit.FILL, background = CanvasBackground.Blur), clip)
        assertEquals(CanvasFit.FILL, p.fit); assertEquals(CanvasBackground.Blur, p.background)
    }
}
