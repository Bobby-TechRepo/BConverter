package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReverseGraphTest {
    private fun result(vararg clips: io.bsoft.bconverter.domain.timeline.VideoClip) = RenderPlanner.plan(TL.project(TL.timeline(*clips)))
    private fun plan(vararg clips: io.bsoft.bconverter.domain.timeline.VideoClip) = (result(*clips) as RenderPlanResult.Success).plan

    @Test fun the_plan_marks_a_reversed_clip() {
        val p = plan(TL.video("a", 2_000).copy(reversed = true), TL.video("b", 2_000))
        assertEquals(listOf(true, false), p.videoSegments.map { it.reversed }); assertEquals(listOf(true, false), p.originalAudio!!.map { it.reversed })
    }
    @Test fun the_picture_is_reversed_after_scaling_and_before_retiming() {
        val f = FilterGraphBuilder.build(plan(TL.video("a", 2_000).copy(reversed = true))).chains.first { it.outputLabels == listOf("v0") }.filters
        val scale = f.indexOfFirst { it.startsWith("scale=") }
        assertEquals("reverse", f[scale + 1]); assertTrue(f.indexOf("reverse") < f.indexOfFirst { it.startsWith("setpts=") })
    }
    @Test fun the_sound_is_reversed_before_tempo_and_padding() {
        val f = FilterGraphBuilder.build(plan(TL.video("a", 2_000).copy(reversed = true, playbackSpeed = 2f))).chains.first { it.outputLabels == listOf("a0") }.filters
        assertTrue("$f", f.indexOf("areverse") < f.indexOfFirst { it.startsWith("atempo") } && f.indexOf("areverse") < f.indexOfFirst { it.startsWith("apad") })
    }
    @Test fun forward_clips_have_no_reverse_filters() {
        val g = FilterGraphBuilder.build(plan(TL.video("a", 2_000)))
        assertTrue(g.chains.none { c -> c.filters.any { it == "reverse" || it == "areverse" } })
    }
    @Test fun a_clip_too_long_to_reverse_fails_the_plan() {
        val r = result(TL.video("a", 60_000).copy(reversed = true))
        val e = (r as RenderPlanResult.Failure).error as RenderError.ReverseTooLong
        assertEquals("a", e.clipId); assertTrue(e.maxDurationMs in 1_000..10_000)
    }
    @Test fun the_same_long_clip_is_fine_forwards() { assertTrue(result(TL.video("a", 60_000)) is RenderPlanResult.Success) }
    @Test fun speed_does_not_relax_the_limit_because_frames_are_buffered_before_the_speed_change() {
        val limit = ReversePolicy.maxDurationMs(1920, 1080, 30.0)
        assertTrue(result(TL.video("a", limit * 2).copy(reversed = true, playbackSpeed = 2f)) is RenderPlanResult.Failure)
    }
}
