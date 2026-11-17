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
class PipStyleRenderTest {
    private fun pip(layout: PipLayout = PipLayout(), fin: Long = 0, fout: Long = 0) = PipClip("p", TL.source("p"), 4_000, 500, 3_500, 2_000, layout, 1f, fin, fout)
    private fun plan(c: PipClip) = (RenderPlanner.plan(TL.project(TL.timeline(TL.video("a", 10_000)).copy(pipClips = listOf(c)), metadata = { TL.meta(audio = true) })) as RenderPlanResult.Success).plan
    private fun look(c: PipClip) = FilterGraphBuilder.build(plan(c)).chains.first { it.outputLabels == listOf("pip1") }.filters

    @Test fun no_border_and_no_fade_leave_the_chain_as_it_was() { assertEquals(listOf("setpts=PTS-STARTPTS+2/TB", "scale=672:-2", "format=rgba"), look(pip())) }
    @Test fun a_border_is_padded_around_the_scaled_picture_before_the_alpha_format() {
        val f = look(pip(PipLayout(borderWidth = 0.02f, borderRgb = 0xFF8800)))
        assertEquals("pad=iw+24:ih+24:12:12:color=0xFF8800", f[2]); assertEquals("format=rgba", f[3])
    }
    @Test fun the_border_is_in_even_pixels_and_at_least_two() {
        assertEquals(2, plan(pip(PipLayout(borderWidth = 0.001f))).pips.single().borderPx)
        assertEquals(0, plan(pip()).pips.single().borderPx)
        assertEquals(0, plan(pip(PipLayout(borderWidth = 0.05f))).pips.single().borderPx % 2)
    }
    @Test fun fades_are_alpha_fades_on_the_timeline_at_the_clips_place() {
        val f = look(pip(fin = 500, fout = 1_000))
        assertTrue(f.toString(), "fade=t=in:st=2:d=0.5:alpha=1" in f); assertTrue(f.toString(), "fade=t=out:st=4:d=1:alpha=1" in f)
    }
    @Test fun the_fades_come_after_the_alpha_format_and_before_the_opacity() {
        val f = look(pip(PipLayout(opacity = 0.5f), fin = 500))
        assertTrue(f.indexOf("format=rgba") < f.indexOfFirst { it.startsWith("fade=") }); assertTrue(f.indexOfFirst { it.startsWith("fade=") } < f.indexOfFirst { it.startsWith("colorchannelmixer") })
    }
    @Test fun a_chroma_key_is_applied_right_after_the_scale_and_before_the_frame() {
        val c = pip(PipLayout(borderWidth = 0.02f)).copy(chromaKey = io.bsoft.bconverter.domain.timeline.ChromaKey(0x00FF00, 0.25f, 0.1f))
        val f = look(c)
        assertEquals("chromakey=color=0x00FF00:similarity=0.25:blend=0.1", f[2]); assertTrue(f[3].startsWith("pad="))
        assertEquals("scale=672:-2", f[1])
    }
    private fun overlay(c: PipClip) = FilterGraphBuilder.build(plan(c)).chains.first { c -> c.filters.any { it.startsWith("overlay=x=") } }.filters.single()
    @Test fun a_clip_without_a_motion_keeps_a_fixed_position() { assertEquals("overlay=x='1440-w/2':y='270-h/2':enable='between(t,2,5)':eof_action=pass:format=auto", overlay(pip(PipLayout()))) }
    @Test fun a_linear_glide_moves_the_centre_with_the_progress_through_the_clip() {
        val f = overlay(pip().copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.25f, 0.75f, eased = false)))
        assertTrue(f, "x='1440+(-960)*clip((t-2)/3,0,1)-w/2'" in f); assertTrue(f, "y='270+(540)*clip((t-2)/3,0,1)-h/2'" in f)
    }
    @Test fun an_eased_glide_uses_the_smoothstep_of_the_progress() {
        val f = overlay(pip().copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.25f, 0.25f, eased = true)))
        val p = "(clip((t-2)/3,0,1)*clip((t-2)/3,0,1)*(3-2*clip((t-2)/3,0,1)))"
        assertTrue(f, "x='1440+(-960)*$p-w/2'" in f)
    }
    @Test fun a_faster_clip_is_retimed_and_placed_on_the_timeline() {
        val c = pip().copy(speed = 2f)
        assertEquals(listOf("setpts=(PTS-STARTPTS)/2+2/TB", "scale=672:-2", "format=rgba"), look(c))
        assertEquals(1_500L, plan(c).pips.single().durationMs)
    }
    @Test fun a_slower_clips_sound_is_stretched_to_match() {
        val o = plan(pip().copy(speed = 0.5f)).overlays.single(); assertEquals(0.5f, o.speed); assertEquals(6_000L, o.durationMs)
    }
    @Test fun a_rectangle_has_no_mask() { assertTrue(look(pip()).none { it.startsWith("geq") }) }
    @Test fun a_rounded_picture_is_masked_after_the_alpha_format_with_its_corner_radius() {
        val f = look(pip().copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.ROUNDED))
        assertEquals("format=rgba", f[f.indexOfFirst { it.startsWith("geq") } - 1]); assertTrue(f.toString(), f.any { it.startsWith("geq=") && "0.15*min(W,H)" in it })
    }
    @Test fun a_circle_is_cut_from_a_square_before_it_is_scaled_and_masked() {
        val f = look(pip().copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE))
        assertEquals("crop=min(iw\\,ih):min(iw\\,ih)", f[1]); assertEquals("scale=672:-2", f[2]); assertTrue(f.toString(), f.any { it.startsWith("geq=") && "min(W,H)/2" in it })
    }
    @Test fun a_shaped_picture_has_no_border() {
        val c = pip(PipLayout(borderWidth = 0.05f)).copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.ROUNDED)
        assertEquals(0, plan(c).pips.single().borderPx); assertTrue(look(c).none { it.startsWith("pad=") })
    }
    @Test fun the_fades_come_after_the_mask() {
        val f = look(pip(fin = 500).copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE))
        assertTrue(f.indexOfFirst { it.startsWith("geq") } < f.indexOfFirst { it.startsWith("fade=") })
    }
    @Test fun a_glide_with_an_end_size_scales_per_frame_and_drops_the_border() {
        val c = pip(PipLayout(borderWidth = 0.05f)).copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.25f, 0.75f, eased = false, endWidth = 0.6f))
        val f = look(c)
        assertTrue(f.toString(), "scale=w='2*trunc((672+(480)*clip((t-2)/3,0,1))/2)':h=-2:eval=frame" == f.last()); assertTrue(f.toString(), "scale=1152:-2" in f)
        assertTrue(f.none { it.startsWith("pad=") }); assertEquals(0, plan(c).pips.single().borderPx); assertEquals(1152, plan(c).pips.single().endWidthPx)
    }
    @Test fun without_an_end_size_the_scale_is_fixed() { assertTrue(look(pip().copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.25f, 0.75f))).contains("scale=672:-2")) }
    private fun kf(t: Long, x: Float, y: Float, w: Float, o: Float) = io.bsoft.bconverter.domain.timeline.PipKeyframe(t, x, y, w, o)
    @Test fun keyframes_become_straight_line_expressions_in_the_overlay() {
        val f = overlay(pip().copy(keyframes = listOf(kf(0, 0.25f, 0.5f, 0.35f, 1f), kf(1_000, 0.75f, 0.5f, 0.35f, 1f), kf(3_000, 0.25f, 0.25f, 0.35f, 1f))))
        assertTrue(f, "x='480+(960)*clip((t-2)/1,0,1)+(-960)*clip((t-3)/2,0,1)-w/2'" in f); assertTrue(f, "y='540+(-270)*clip((t-3)/2,0,1)-h/2'" in f)
    }
    @Test fun a_changing_width_scales_per_frame_and_drops_the_border() {
        val c = pip(PipLayout(borderWidth = 0.05f)).copy(keyframes = listOf(kf(0, 0.5f, 0.5f, 0.2f, 1f), kf(2_000, 0.5f, 0.5f, 0.6f, 1f)))
        val f = look(c)
        assertTrue(f.toString(), "scale=w='2*trunc((384+(768)*clip((t-2)/2,0,1))/2)':h=-2:eval=frame" == f.last()); assertTrue(f.toString(), "scale=1152:-2" in f); assertTrue(f.none { it.startsWith("pad=") }); assertEquals(384, plan(c).pips.single().widthPx)
    }
    @Test fun a_changing_opacity_uses_a_per_frame_alpha() {
        val f = look(pip().copy(keyframes = listOf(kf(0, 0.5f, 0.5f, 0.35f, 0f), kf(2_000, 0.5f, 0.5f, 0.35f, 1f))))
        assertTrue(f.toString(), f.any { it.startsWith("geq=") && "alpha(X,Y)*(0+(1)*clip((T-2)/2,0,1))" in it }); assertTrue(f.none { it.startsWith("colorchannelmixer") })
    }
    @Test fun a_constant_keyframe_opacity_is_a_plain_multiplier() {
        val f = look(pip().copy(keyframes = listOf(kf(0, 0.5f, 0.5f, 0.35f, 0.5f), kf(2_000, 0.9f, 0.5f, 0.35f, 0.5f))))
        assertTrue(f.toString(), "colorchannelmixer=aa=0.5" in f)
    }
    @Test fun an_eased_step_uses_the_smoothstep_of_its_progress() {
        val f = overlay(pip().copy(keyframes = listOf(io.bsoft.bconverter.domain.timeline.PipKeyframe(0, 0.25f, 0.5f, 0.35f, 1f, eased = true), kf(1_000, 0.75f, 0.5f, 0.35f, 1f))))
        val p = "clip((t-2)/1,0,1)"
        assertTrue(f, "x='480+(960)*($p*$p*(3-2*$p))-w/2'" in f)
    }
    @Test fun a_single_keyframe_is_a_fixed_look() {
        val f = overlay(pip().copy(keyframes = listOf(kf(0, 0.25f, 0.5f, 0.35f, 1f))))
        assertEquals("overlay=x='480-w/2':y='540-h/2':enable='between(t,2,5)':eof_action=pass:format=auto", f)
    }
    @Test fun the_size_is_animated_last_so_masks_and_opacity_work_at_a_fixed_size() {
        val f = look(pip().copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE, keyframes = listOf(kf(0, 0.5f, 0.5f, 0.2f, 0f), kf(2_000, 0.5f, 0.5f, 0.6f, 1f))))
        val last = f.indexOfLast { it.startsWith("scale=w=") }
        assertEquals(f.lastIndex, last); assertTrue(f.indexOfFirst { it.startsWith("geq") } < last); assertTrue(f.indexOfLast { it.startsWith("geq") } < last)
    }
    @Test fun the_plan_carries_the_motion() { assertEquals(io.bsoft.bconverter.domain.timeline.PipMotion(0.1f, 0.2f), plan(pip().copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.1f, 0.2f))).pips.single().motion) }
    @Test fun without_a_key_there_is_no_chromakey_filter() { assertFalse(look(pip()).any { it.startsWith("chromakey") }) }
    @Test fun no_fade_adds_no_fade_filter() { assertFalse(look(pip()).any { it.startsWith("fade=") }) }
    @Test fun fades_that_do_not_fit_are_fitted_in_the_plan() {
        val s = plan(pip(fin = 9_000, fout = 9_000)).pips.single(); assertEquals(3_000L, s.fadeInMs); assertEquals(0L, s.fadeOutMs)
    }
    @Test fun the_clips_own_sound_fades_with_it() {
        val a = plan(pip(fin = 500, fout = 700)).overlays.single(); assertEquals(500L, a.fadeInMs); assertEquals(700L, a.fadeOutMs)
    }
}
