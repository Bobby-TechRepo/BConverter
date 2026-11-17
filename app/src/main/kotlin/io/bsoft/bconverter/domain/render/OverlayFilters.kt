package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayAnimator
import io.bsoft.bconverter.domain.overlay.OverlayEasing
import io.bsoft.bconverter.domain.overlay.OverlayLayer
import io.bsoft.bconverter.domain.overlay.TextOverlay
import java.util.Locale

/** The filters that lay the overlay pictures over the video. */
data class OverlayChain(
    /** Filter graph parts to append to the render's filter graph (each ends with a label). */
    val parts: List<String>,
    /** The video label after all overlays; equal to the input label when there are none. */
    val output: String,
)

/**
 * Builds the FFmpeg filters that draw each overlay picture over the video, back to front. Every picture is an extra
 * looped-image input; its overlay is limited to the overlay's time and positioned by its centre:
 *  - slide animations move the picture with an expression of the time;
 *  - scale animations resize it every frame with the `scale` filter (the centre stays put);
 *  - fades use the `fade` filter on the picture's alpha, which is linear (FFmpeg has no eased video fade).
 * The in/out lengths and constants are the ones [OverlayAnimator] uses for the preview.
 */
object OverlayFilters {
    /**
     * @param firstInput FFmpeg input index of the first picture (the others follow in order)
     * @param videoLabel the label of the video the overlays go on
     */
    fun build(layers: List<OverlayLayer>, canvasWidth: Int, canvasHeight: Int, firstInput: Int, videoLabel: String): OverlayChain {
        val parts = mutableListOf<String>()
        var current = videoLabel
        layers.forEachIndexed { i, layer ->
            val o = layer.overlay
            val picture = "ovp${i + 1}"
            val next = "ovv${i + 1}"
            parts += "[${firstInput + i}:v]" + pictureFilters(o).joinToString(",") + "[$picture]"
            parts += "[$current][$picture]overlay=x='${xExpression(o, canvasWidth)}':y='${yExpression(o, canvasHeight)}'" +
                ":enable='gte(t,${sec(o.startMs)})*lt(t,${sec(o.endMs)})':eof_action=pass:format=auto[$next]"
            current = next
        }
        return OverlayChain(parts, current)
    }

    private fun pictureFilters(o: TextOverlay): List<String> = buildList {
        add("format=rgba")
        val (inMs, outMs) = lengths(o)
        val kf = o.keyframes
        // Per-frame opacity first: `geq` keeps the size of its first frame, so it must come before any filter that resizes per frame.
        if (kf.map { it.opacity }.distinct().size > 1) add("geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='alpha(X,Y)*(${FilterGraphBuilder.piecewise(o.startMs, kf.map { it.offsetMs to it.opacity.toDouble() }, "T", 3, kf.map { it.eased })})'")
        else if (kf.size == 1 && kf[0].opacity < 1f) add("colorchannelmixer=aa=${num(kf[0].opacity)}")
        // The keyframes' extra turn about the picture's centre. The result is always a square as long as the picture's diagonal (`rotate`
        // sizes its output once, so it cannot follow the angle), which keeps the centre where it was; corners stay transparent.
        rotationExpression(o)?.let { add("rotate=a='$it':ow='hypot(iw,ih)':oh='hypot(iw,ih)':c=black@0") }
        scaleExpression(o, inMs, outMs)?.let { add("scale=w='max(2,trunc(iw*($it)/2)*2)':h='max(2,trunc(ih*($it)/2)*2)':eval=frame") }
        if (o.animIn?.kind == AnimationKind.FADE && inMs > 0) add("fade=t=in:st=${sec(o.startMs)}:d=${sec(inMs)}:alpha=1")
        if (o.animOut?.kind == AnimationKind.FADE && outMs > 0) add("fade=t=out:st=${sec(o.endMs - outMs)}:d=${sec(outMs)}:alpha=1")
    }

    /** In and out lengths as the preview uses them: shrunk in proportion when together longer than the overlay. */
    private fun lengths(o: TextOverlay): Pair<Long, Long> {
        var inMs = o.animIn?.durationMs ?: 0L
        var outMs = o.animOut?.durationMs ?: 0L
        if (inMs + outMs > o.durationMs) {
            val k = o.durationMs.toDouble() / (inMs + outMs)
            inMs = (inMs * k).toLong(); outMs = (outMs * k).toLong()
        }
        return inMs to outMs
    }

    private fun xExpression(o: TextOverlay, w: Int): String = "${centre(o, w, true)}${offset(o, w, AnimationKind.SLIDE_LEFT, AnimationKind.SLIDE_RIGHT)}-w/2"
    private fun yExpression(o: TextOverlay, h: Int): String = "${centre(o, h, false)}${offset(o, h, AnimationKind.SLIDE_UP, AnimationKind.SLIDE_DOWN)}-h/2"

    /** The centre along one axis in pixels: fixed, or straight lines between the keyframes as an expression of the time. */
    private fun centre(o: TextOverlay, size: Int, horizontal: Boolean): String {
        val kf = o.keyframes
        if (kf.isEmpty()) return num((if (horizontal) o.centerX else o.centerY) * size)
        return FilterGraphBuilder.piecewise(o.startMs, kf.map { it.offsetMs to (if (horizontal) it.x else it.y) * size.toDouble() }, "t", eased = kf.map { it.eased })
    }

    /** Extra "+term" parts for the slide animations along one axis; [negative] moves towards 0, [positive] away from it. */
    private fun offset(o: TextOverlay, size: Int, negative: AnimationKind, positive: AnimationKind): String {
        val (inMs, outMs) = lengths(o)
        val distance = OverlayAnimator.SLIDE_DISTANCE * size
        return buildString {
            o.animIn?.takeIf { inMs > 0 && (it.kind == negative || it.kind == positive) }?.let {
                val sign = if (it.kind == negative) "-" else "+"
                append("$sign${num(distance)}*(1-${eased(it.easing, "clip((t-${sec(o.startMs)})/${sec(inMs)},0,1)")})")
            }
            o.animOut?.takeIf { outMs > 0 && (it.kind == negative || it.kind == positive) }?.let {
                val sign = if (it.kind == negative) "-" else "+"
                append("$sign${num(distance)}*${eased(it.easing, "clip((t-${sec(o.endMs - outMs)})/${sec(outMs)},0,1)")}")
            }
        }
    }

    /** The keyframes' turn in radians as an expression of the time, or null when nothing turns. */
    private fun rotationExpression(o: TextOverlay): String? {
        val turns = o.keyframes.map { it.offsetMs to it.rotationDeg.toDouble() }
        if (turns.map { it.second }.distinct().size > 1) return "(${FilterGraphBuilder.piecewise(o.startMs, turns, "t", 3, o.keyframes.map { it.eased })})*PI/180"
        val only = turns.singleOrNull()?.second ?: turns.firstOrNull()?.second ?: return null
        return if (only == 0.0) null else "${num(only.toFloat())}*PI/180"
    }

    /** The scale factor over time, or null when neither animation scales. 0.5 → 1 coming in, 1 → 0.5 going out. */
    private fun scaleExpression(o: TextOverlay, inMs: Long, outMs: Long): String? {
        val parts = mutableListOf<String>()
        o.animIn?.takeIf { it.kind == AnimationKind.SCALE && inMs > 0 }?.let {
            parts += "(${num(OverlayAnimator.SCALE_FROM)}+${num(1f - OverlayAnimator.SCALE_FROM)}*${eased(it.easing, "clip((t-${sec(o.startMs)})/${sec(inMs)},0,1)")})"
        }
        o.animOut?.takeIf { it.kind == AnimationKind.SCALE && outMs > 0 }?.let {
            parts += "(1-${num(1f - OverlayAnimator.SCALE_FROM)}*${eased(it.easing, "clip((t-${sec(o.endMs - outMs)})/${sec(outMs)},0,1)")})"
        }
        // The keyframes' zoom (1 = the picture's own size).
        val zooms = o.keyframes.map { it.offsetMs to it.zoom.toDouble() }
        if (zooms.map { it.second }.distinct().size > 1) parts += "(${FilterGraphBuilder.piecewise(o.startMs, zooms, "t", 3, o.keyframes.map { it.eased })})"
        else if (zooms.size == 1 && zooms[0].second != 1.0) parts += num(zooms[0].second.toFloat())
        return parts.joinToString("*").ifEmpty { null }
    }

    /** The easing of [OverlayAnimator] written as an FFmpeg expression of progress [p] (0..1). */
    fun eased(e: OverlayEasing, p: String): String = when (e) {
        OverlayEasing.LINEAR -> p
        OverlayEasing.EASE_IN -> "pow($p,2)"
        OverlayEasing.EASE_OUT -> "(1-pow(1-$p,2))"
        OverlayEasing.EASE_IN_OUT -> "if(lt($p,0.5),2*pow($p,2),1-2*pow(1-$p,2))"
    }

    private fun sec(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms / 1000.0).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    private fun num(v: Float): String = String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
