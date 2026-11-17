package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayEasing
import io.bsoft.bconverter.domain.overlay.OverlayLayer
import io.bsoft.bconverter.domain.overlay.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OverlayFiltersTest {
    private fun layer(o: TextOverlay) = OverlayLayer(o, File("/tmp/x.png"), 200, 80)
    private fun lin(k: AnimationKind, ms: Long = 500) = OverlayAnimation(k, ms, OverlayEasing.LINEAR)
    private fun build(vararg o: TextOverlay, first: Int = 1) = OverlayFilters.build(o.map(::layer), 1000, 500, first, "vout")
    private val plain = TextOverlay("a", "Hi", 1000, 3500, centerX = 0.5f, centerY = 0.25f)

    @Test fun no_overlays_leaves_the_video_label_alone() {
        val c = OverlayFilters.build(emptyList(), 1000, 500, 1, "vout")
        assertEquals("vout", c.output); assertTrue(c.parts.isEmpty())
    }
    @Test fun a_plain_overlay_is_a_timed_overlay_at_its_centre() {
        val c = build(plain)
        assertEquals("ovv1", c.output)
        assertEquals("[1:v]format=rgba[ovp1]", c.parts[0])
        assertEquals("[vout][ovp1]overlay=x='500-w/2':y='125-h/2':enable='gte(t,1)*lt(t,3.5)':eof_action=pass:format=auto[ovv1]", c.parts[1])
    }
    private fun kf(t: Long, x: Float, y: Float, z: Float, o: Float) = io.bsoft.bconverter.domain.overlay.OverlayKeyframe(t, x, y, z, o)
    @Test fun keyframes_move_the_centre_in_straight_lines() {
        val c = build(plain.copy(keyframes = listOf(kf(0, 0.25f, 0.5f, 1f, 1f), kf(1_000, 0.75f, 0.5f, 1f, 1f), kf(2_000, 0.25f, 0.2f, 1f, 1f))))
        assertEquals("[vout][ovp1]overlay=x='250+(500)*clip((t-1)/1,0,1)+(-500)*clip((t-2)/1,0,1)-w/2':y='250+(-150)*clip((t-2)/1,0,1)-h/2':enable='gte(t,1)*lt(t,3.5)':eof_action=pass:format=auto[ovv1]", c.parts[1])
    }
    @Test fun an_eased_step_uses_the_smoothstep_of_its_progress() {
        val c = build(plain.copy(keyframes = listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.25f, 0.5f, eased = true), kf(1_000, 0.75f, 0.5f, 1f, 1f))))
        val p = "clip((t-1)/1,0,1)"
        assertTrue(c.parts[1], "x='250+(500)*($p*$p*(3-2*$p))-w/2'" in c.parts[1])
    }
    @Test fun a_zoom_resizes_the_picture_every_frame() {
        val c = build(plain.copy(keyframes = listOf(kf(0, 0.5f, 0.25f, 0.5f, 1f), kf(1_000, 0.5f, 0.25f, 1.5f, 1f))))
        assertEquals("[1:v]format=rgba,scale=w='max(2,trunc(iw*((0.5+(1)*clip((t-1)/1,0,1)))/2)*2)':h='max(2,trunc(ih*((0.5+(1)*clip((t-1)/1,0,1)))/2)*2)':eval=frame[ovp1]", c.parts[0])
    }
    @Test fun a_changing_opacity_is_a_per_frame_alpha_before_any_resizing() {
        val p = build(plain.copy(keyframes = listOf(kf(0, 0.5f, 0.25f, 0.5f, 0f), kf(1_000, 0.5f, 0.25f, 1.5f, 1f)))).parts[0]
        assertTrue(p, p.contains("geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='alpha(X,Y)*(0+(1)*clip((T-1)/1,0,1))'")); assertTrue(p, p.indexOf("geq=") < p.indexOf("scale="))
    }
    @Test fun a_constant_opacity_and_zoom_are_plain() {
        val p = build(plain.copy(keyframes = listOf(kf(0, 0.5f, 0.25f, 0.5f, 0.5f)))).parts[0]
        assertEquals("[1:v]format=rgba,colorchannelmixer=aa=0.5,scale=w='max(2,trunc(iw*(0.5)/2)*2)':h='max(2,trunc(ih*(0.5)/2)*2)':eval=frame[ovp1]", p)
    }
    private fun kr(t: Long, r: Float, eased: Boolean = false) = io.bsoft.bconverter.domain.overlay.OverlayKeyframe(t, 0.5f, 0.25f, 1f, 1f, eased, r)
    @Test fun a_changing_turn_rotates_the_picture_every_frame_inside_a_square_as_long_as_its_diagonal() {
        val p = build(plain.copy(keyframes = listOf(kr(0, 0f), kr(1_000, 90f)))).parts[0]
        assertEquals("[1:v]format=rgba,rotate=a='(0+(90)*clip((t-1)/1,0,1))*PI/180':ow='hypot(iw,ih)':oh='hypot(iw,ih)':c=black@0[ovp1]", p)
    }
    @Test fun a_constant_turn_is_a_fixed_angle() {
        assertEquals("[1:v]format=rgba,rotate=a='45*PI/180':ow='hypot(iw,ih)':oh='hypot(iw,ih)':c=black@0[ovp1]", build(plain.copy(keyframes = listOf(kr(0, 45f)))).parts[0])
    }
    @Test fun no_turn_adds_no_rotation_filter() {
        assertEquals("[1:v]format=rgba[ovp1]", build(plain.copy(keyframes = listOf(kr(0, 0f), kr(500, 0f)))).parts[0])
    }
    @Test fun the_turn_comes_after_the_opacity_and_before_any_resizing() {
        val p = build(plain.copy(keyframes = listOf(
            io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.5f, 0.25f, 0.5f, 0f, false, 0f),
            io.bsoft.bconverter.domain.overlay.OverlayKeyframe(1_000, 0.5f, 0.25f, 1.5f, 1f, false, 90f),
        ))).parts[0]
        assertTrue(p, p.indexOf("geq=") < p.indexOf("rotate=")); assertTrue(p, p.indexOf("rotate=") < p.indexOf("scale="))
    }
    @Test fun an_eased_turn_uses_the_smoothstep() {
        val p = build(plain.copy(keyframes = listOf(kr(0, 0f, eased = true), kr(1_000, 90f)))).parts[0]
        val q = "clip((t-1)/1,0,1)"
        assertTrue(p, "(0+(90)*($q*$q*(3-2*$q)))*PI/180" in p)
    }
    @Test fun overlays_stack_in_list_order_and_use_consecutive_inputs() {
        val c = build(plain, plain.copy(id = "b"), first = 4)
        assertTrue(c.parts[0].startsWith("[4:v]")); assertTrue(c.parts[2].startsWith("[5:v]"))
        assertTrue(c.parts[1].startsWith("[vout][ovp1]")); assertTrue(c.parts[3].startsWith("[ovv1][ovp2]"))
        assertEquals("ovv2", c.output)
    }
    @Test fun fade_in_and_out_use_alpha_fades_at_absolute_times() {
        val c = build(plain.copy(animIn = lin(AnimationKind.FADE, 400), animOut = lin(AnimationKind.FADE, 600)))
        assertEquals("[1:v]format=rgba,fade=t=in:st=1:d=0.4:alpha=1,fade=t=out:st=2.9:d=0.6:alpha=1[ovp1]", c.parts[0])
    }
    @Test fun slide_left_in_starts_left_and_arrives_at_the_centre() {
        val x = build(plain.copy(animIn = lin(AnimationKind.SLIDE_LEFT))).parts[1]
        assertTrue(x, x.contains("x='500-200*(1-clip((t-1)/0.5,0,1))-w/2'"))
        assertTrue(x, x.contains("y='125-h/2'"))
    }
    @Test fun slide_up_out_moves_up_at_the_end() {
        val x = build(plain.copy(animOut = lin(AnimationKind.SLIDE_UP))).parts[1]
        assertTrue(x, x.contains("y='125-100*clip((t-3)/0.5,0,1)-h/2'"))
    }
    @Test fun slide_right_and_down_move_the_other_way() {
        val x = build(plain.copy(animIn = lin(AnimationKind.SLIDE_RIGHT), animOut = lin(AnimationKind.SLIDE_DOWN))).parts[1]
        assertTrue(x, x.contains("x='500+200*(1-clip((t-1)/0.5,0,1))-w/2'"))
        assertTrue(x, x.contains("y='125+100*clip((t-3)/0.5,0,1)-h/2'"))
    }
    @Test fun scale_in_resizes_every_frame_from_half() {
        val p = build(plain.copy(animIn = lin(AnimationKind.SCALE))).parts[0]
        assertTrue(p, p.contains("scale=w='max(2,trunc(iw*((0.5+0.5*clip((t-1)/0.5,0,1)))/2)*2)'"))
        assertTrue(p, p.contains(":eval=frame"))
    }
    @Test fun scale_in_and_out_multiply() {
        val p = build(plain.copy(animIn = lin(AnimationKind.SCALE), animOut = lin(AnimationKind.SCALE))).parts[0]
        assertTrue(p, p.contains(")*(1-0.5*clip((t-3)/0.5,0,1))"))
    }
    @Test fun no_scale_filter_without_a_scale_animation() = assertFalse(build(plain.copy(animIn = lin(AnimationKind.FADE))).parts[0].contains("scale="))
    @Test fun easings_are_written_as_expressions() {
        assertEquals("p", OverlayFilters.eased(OverlayEasing.LINEAR, "p"))
        assertEquals("pow(p,2)", OverlayFilters.eased(OverlayEasing.EASE_IN, "p"))
        assertEquals("(1-pow(1-p,2))", OverlayFilters.eased(OverlayEasing.EASE_OUT, "p"))
        assertTrue(OverlayFilters.eased(OverlayEasing.EASE_IN_OUT, "p").startsWith("if(lt(p,0.5)"))
    }
    @Test fun animations_longer_than_the_overlay_shrink_in_proportion() {
        val o = plain.copy(startMs = 0, endMs = 600, animIn = lin(AnimationKind.FADE, 500), animOut = lin(AnimationKind.FADE, 500))
        val p = build(o).parts[0]
        assertTrue(p, p.contains("fade=t=in:st=0:d=0.3:alpha=1")); assertTrue(p, p.contains("fade=t=out:st=0.3:d=0.3:alpha=1"))
    }
    @Test fun output_has_no_unbalanced_quotes_or_parentheses() {
        val o = plain.copy(animIn = OverlayAnimation(AnimationKind.SCALE, 700, OverlayEasing.EASE_IN_OUT), animOut = OverlayAnimation(AnimationKind.SLIDE_LEFT, 300, OverlayEasing.EASE_IN_OUT))
        build(o).parts.forEach { s ->
            assertEquals(s, s.count { it == '(' }, s.count { it == ')' }); assertEquals(s, 0, s.count { it == '\'' } % 2)
        }
    }
}
