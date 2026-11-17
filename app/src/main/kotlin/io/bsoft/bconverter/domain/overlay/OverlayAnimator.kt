package io.bsoft.bconverter.domain.overlay

/** How an overlay looks at one instant: [offsetX]/[offsetY] are fractions of the frame added to its position; [rotationDeg] is a clockwise turn added to its own. */
data class OverlayFrame(val alpha: Float, val offsetX: Float, val offsetY: Float, val scale: Float, val rotationDeg: Float = 0f) {
    companion object { val Still = OverlayFrame(1f, 0f, 0f, 1f) }
}

/** Pure evaluation of the in/out animations. The preview and the renderer both use it so they agree. */
object OverlayAnimator {
    const val SLIDE_DISTANCE = 0.2f
    const val SCALE_FROM = 0.5f

    /** Null when the overlay is not shown at [timeMs]. */
    fun frameAt(o: TextOverlay, timeMs: Long): OverlayFrame? {
        if (!o.isShownAt(timeMs)) return null
        var inMs = o.animIn?.durationMs ?: 0L
        var outMs = o.animOut?.durationMs ?: 0L
        if (inMs + outMs > o.durationMs) { // never overlap: shrink both in proportion
            val k = o.durationMs.toDouble() / (inMs + outMs)
            inMs = (inMs * k).toLong(); outMs = (outMs * k).toLong()
        }
        var frame = OverlayFrame.Still
        o.animIn?.takeIf { inMs > 0 && timeMs - o.startMs < inMs }?.let {
            val p = ease(it.easing, (timeMs - o.startMs).toFloat() / inMs)
            frame = apply(frame, it.kind, 1f - p, entering = true)
        }
        o.animOut?.takeIf { outMs > 0 && timeMs >= o.endMs - outMs }?.let {
            val q = ease(it.easing, (timeMs - (o.endMs - outMs)).toFloat() / outMs)
            frame = apply(frame, it.kind, q, entering = false)
        }
        if (o.keyframes.isNotEmpty()) {
            val l = OverlayKeyframes.interpolate(o.keyframes, timeMs - o.startMs)
            frame = frame.copy(alpha = frame.alpha * l.opacity, offsetX = frame.offsetX + (l.x - o.centerX), offsetY = frame.offsetY + (l.y - o.centerY), scale = frame.scale * l.zoom, rotationDeg = frame.rotationDeg + l.rotationDeg)
        }
        return frame
    }

    /** [away] is 0 when fully in place and 1 when fully gone. */
    private fun apply(f: OverlayFrame, kind: AnimationKind, away: Float, entering: Boolean): OverlayFrame = when (kind) {
        AnimationKind.FADE -> f.copy(alpha = f.alpha * (1f - away))
        AnimationKind.SCALE -> f.copy(scale = f.scale * (1f + (SCALE_FROM - 1f) * away))
        AnimationKind.SLIDE_LEFT -> f.copy(offsetX = f.offsetX - SLIDE_DISTANCE * away)
        AnimationKind.SLIDE_RIGHT -> f.copy(offsetX = f.offsetX + SLIDE_DISTANCE * away)
        AnimationKind.SLIDE_UP -> f.copy(offsetY = f.offsetY - SLIDE_DISTANCE * away)
        AnimationKind.SLIDE_DOWN -> f.copy(offsetY = f.offsetY + SLIDE_DISTANCE * away)
        else -> f // unknown kinds are kept in the project but do not animate
    }.also { entering.hashCode() }

    private fun ease(e: OverlayEasing, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (e) {
            OverlayEasing.LINEAR -> x
            OverlayEasing.EASE_IN -> x * x
            OverlayEasing.EASE_OUT -> 1f - (1f - x) * (1f - x)
            OverlayEasing.EASE_IN_OUT -> if (x < 0.5f) 2f * x * x else 1f - 2f * (1f - x) * (1f - x)
        }
    }
}
