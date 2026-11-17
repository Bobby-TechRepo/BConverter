package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PipRenderTest {
    private fun pip(id: String = "p", start: Long = 2_000, span: Long = 3_000, layout: PipLayout = PipLayout(), volume: Float = 1f) =
        PipClip(id, TL.source(id), span + 1_000, 500, 500 + span, start, layout, volume)
    private fun timeline(vararg p: PipClip) = TL.timeline(TL.video("a", 10_000)).copy(pipClips = p.toList())
    private fun result(t: Timeline, hasAudio: Boolean = true) = RenderPlanner.plan(TL.project(t, metadata = { TL.meta(audio = hasAudio) }))
    private fun plan(t: Timeline, hasAudio: Boolean = true) = (result(t, hasAudio) as RenderPlanResult.Success).plan

    @Test fun without_pips_nothing_changes() {
        val g = FilterGraphBuilder.build(plan(timeline())); assertEquals("vout", g.videoOutput); assertTrue(plan(timeline()).pips.isEmpty())
    }
    @Test fun a_pip_is_an_extra_input_after_the_clips() {
        val p = plan(timeline(pip())); assertEquals(2, p.inputs.size)
        assertEquals(500L, p.inputs[1].seekMs); assertEquals(3_000L, p.inputs[1].durationMs); assertEquals(1, p.pips.single().inputIndex)
    }
    @Test fun pip_inputs_follow_audio_clip_inputs() {
        val t = TL.withAudio(timeline(pip()), listOf(TL.audio("m")))
        val p = plan(t); assertEquals(listOf(1, 2).let { 2 }, p.pips.single().inputIndex); assertEquals(3, p.inputs.size)
    }
    @Test fun its_width_is_a_share_of_the_canvas_in_even_pixels() {
        assertEquals(672, plan(timeline(pip(layout = PipLayout(width = 0.35f)))).pips.single().widthPx)
        assertEquals(0, plan(timeline(pip(layout = PipLayout(width = 0.35f)))).pips.single().widthPx % 2)
    }
    @Test fun the_graph_places_it_at_its_time_and_position() {
        val chains = FilterGraphBuilder.build(plan(timeline(pip(layout = PipLayout(0.75f, 0.25f, 0.35f, 1f))))).chains
        val look = chains.first { it.outputLabels == listOf("pip1") }; assertEquals(listOf("1:v"), look.inputLabels)
        assertEquals(listOf("setpts=PTS-STARTPTS+2/TB", "scale=672:-2", "format=rgba"), look.filters)
        val over = chains.first { it.outputLabels == listOf("pv1") }; assertEquals(listOf("vout", "pip1"), over.inputLabels)
        assertEquals("overlay=x='1440-w/2':y='270-h/2':enable='between(t,2,5)':eof_action=pass:format=auto", over.filters.single())
    }
    @Test fun opacity_below_one_adds_an_alpha_multiplier() {
        val look = FilterGraphBuilder.build(plan(timeline(pip(layout = PipLayout(opacity = 0.5f))))).chains.first { it.outputLabels == listOf("pip1") }
        assertTrue(look.filters.contains("colorchannelmixer=aa=0.5"))
    }
    @Test fun full_opacity_adds_nothing() {
        assertFalse(FilterGraphBuilder.build(plan(timeline(pip()))).chains.first { it.outputLabels == listOf("pip1") }.filters.any { it.startsWith("colorchannelmixer") })
    }
    @Test fun pips_are_stacked_in_order_and_the_last_one_is_the_graphs_output() {
        val g = FilterGraphBuilder.build(plan(timeline(pip("p"), pip("q", start = 4_000))))
        assertEquals("pv2", g.videoOutput); assertEquals(listOf("pv1", "pip2"), g.chains.first { it.outputLabels == listOf("pv2") }.inputLabels)
    }
    @Test fun the_pips_own_sound_is_mixed_in_at_its_start_and_volume() {
        val o = plan(timeline(pip(volume = 0.5f))).overlays.single()
        assertEquals(1, o.inputIndex); assertEquals(2_000L, o.delayMs); assertEquals(0.5f, o.volume, 0f); assertEquals(3_000L, o.durationMs)
    }
    @Test fun a_muted_pip_or_one_without_sound_adds_no_audio() {
        assertTrue(plan(timeline(pip(volume = 0f))).overlays.isEmpty()); assertTrue(plan(timeline(pip()), hasAudio = false).overlays.isEmpty())
    }
    @Test fun a_pip_whose_source_is_unknown_fails_the_plan() {
        val r = RenderPlanner.plan(TL.project(timeline(pip()), metadata = { s -> if ((s as io.bsoft.bconverter.domain.model.MediaSource.FileSource).file.name == "p") null else TL.meta() }))
        assertTrue((r as RenderPlanResult.Failure).error is RenderError.MissingSourceInfo)
    }
    @Test fun a_pip_that_is_not_staged_fails_the_plan() {
        val t = timeline(pip().copy(source = io.bsoft.bconverter.domain.model.MediaSource.UriSource(android.net.Uri.parse("content://x/1"))))
        assertTrue((result(t) as RenderPlanResult.Failure).error is RenderError.SourceNotStaged)
    }
    @Test fun the_output_length_is_still_the_main_videos() { assertEquals(10_000L, plan(timeline(pip(start = 9_000, span = 4_000))).durationMs) }
}
