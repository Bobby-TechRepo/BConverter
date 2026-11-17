package io.bsoft.bconverter.domain.overlay

/**
 * One point of a text or picture overlay's animation: [offsetMs] after the overlay starts, it is centred at ([x], [y]) (shares of the
 * frame), drawn at [zoom] times its own size and at [opacity] times its own opacity. Between two keyframes everything changes in a
 * straight line (or, when [eased], starting and ending slowly); before the first and after the last it holds still.
 * [rotationDeg] is an extra clockwise turn added to the overlay's own rotation (0 = as it is).
 */
data class OverlayKeyframe(val offsetMs: Long, val x: Float, val y: Float, val zoom: Float = 1f, val opacity: Float = 1f, val eased: Boolean = false, val rotationDeg: Float = 0f) {
    init {
        require(offsetMs >= 0) { "offset must be >= 0" }
        require(x.isFinite() && y.isFinite()) { "centre must be finite" }
        require(zoom.isFinite() && zoom in OverlayKeyframes.MIN_ZOOM..OverlayKeyframes.MAX_ZOOM) { "zoom out of range" }
        require(opacity.isFinite() && opacity in 0f..1f) { "opacity must be within 0..1" }
        require(rotationDeg.isFinite() && rotationDeg in -OverlayKeyframes.MAX_TURN_DEG..OverlayKeyframes.MAX_TURN_DEG) { "turn out of range" }
    }
}

/** How an overlay looks at a moment of its keyframe animation. */
data class OverlayLook(val x: Float, val y: Float, val zoom: Float, val opacity: Float, val rotationDeg: Float = 0f)

object OverlayKeyframes {
    const val MAX = 16
    const val MIN_ZOOM = 0.1f
    /** The picture is drawn once at the overlay's own size, so a zoom above 2 would only be blurry. */
    const val MAX_ZOOM = 2f
    /** Two full circles either way. */
    const val MAX_TURN_DEG = 720f

    /** Strictly increasing offsets and at most [MAX]. */
    fun isOrdered(list: List<OverlayKeyframe>): Boolean = list.size <= MAX && list.zipWithNext().all { (a, b) -> a.offsetMs < b.offsetMs }

    /** The keyframes that still fall within an overlay of [durationMs] (after it is shortened). */
    fun fitted(list: List<OverlayKeyframe>, durationMs: Long): List<OverlayKeyframe> = list.filter { it.offsetMs <= durationMs }

    fun interpolate(list: List<OverlayKeyframe>, offsetMs: Long): OverlayLook {
        require(list.isNotEmpty()) { "no keyframes" }
        fun look(k: OverlayKeyframe) = OverlayLook(k.x, k.y, k.zoom, k.opacity, k.rotationDeg)
        if (offsetMs <= list.first().offsetMs) return look(list.first())
        if (offsetMs >= list.last().offsetMs) return look(list.last())
        val i = list.indexOfLast { it.offsetMs <= offsetMs }
        val a = list[i]; val b = list[i + 1]
        val raw = (offsetMs - a.offsetMs).toFloat() / (b.offsetMs - a.offsetMs)
        val f = if (a.eased) raw * raw * (3 - 2 * raw) else raw
        fun mix(p: Float, q: Float) = p + (q - p) * f
        return OverlayLook(mix(a.x, b.x), mix(a.y, b.y), mix(a.zoom, b.zoom), mix(a.opacity, b.opacity), mix(a.rotationDeg, b.rotationDeg))
    }

    /**
     * The keyframes after adding one at [offsetMs] from the overlay's start, with the look it has there. The first one is the
     * overlay as it stands (its own position, zoom 1, opacity 1) so nothing jumps. Unchanged when the moment is outside the overlay,
     * already has a keyframe, or there are too many.
     */
    fun addAt(o: TextOverlay, offsetMs: Long): List<OverlayKeyframe> {
        if (offsetMs !in 0..o.durationMs) return o.keyframes
        val base = o.keyframes.ifEmpty { listOf(OverlayKeyframe(0, o.centerX, o.centerY)) }
        if (base.any { it.offsetMs == offsetMs } || base.size >= MAX) return base
        val l = interpolate(base, offsetMs)
        return (base + OverlayKeyframe(offsetMs, l.x, l.y, l.zoom, l.opacity, rotationDeg = l.rotationDeg)).sortedBy { it.offsetMs }
    }

    /** A keyframe this close (ms) to a moment counts as being at it, so a drag at the playhead keeps editing the same one. */
    const val SNAP_MS = 50L

    /**
     * The keyframes after the overlay is given [look] at [offsetMs]: a keyframe within [SNAP_MS] of the moment takes the look (keeping its
     * time and easing), otherwise a new one is added there. Unchanged when the moment is outside the overlay or there are too many.
     */
    fun withLookAt(o: TextOverlay, offsetMs: Long, look: OverlayLook): List<OverlayKeyframe> {
        val now = o.keyframes
        if (offsetMs !in 0..o.durationMs) return now
        fun frame(t: Long, eased: Boolean) = OverlayKeyframe(t, look.x.coerceIn(0f, 1f), look.y.coerceIn(0f, 1f), look.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM), look.opacity.coerceIn(0f, 1f), eased, look.rotationDeg.coerceIn(-MAX_TURN_DEG, MAX_TURN_DEG))
        val near = now.indexOfFirst { kotlin.math.abs(it.offsetMs - offsetMs) <= SNAP_MS }
        if (near >= 0) return now.mapIndexed { i, k -> if (i == near) frame(k.offsetMs, k.eased) else k }
        if (now.size >= MAX) return now
        return (now + frame(offsetMs, false)).sortedBy { it.offsetMs }
    }

    /** The overlay as it stands at [timeMs] on the timeline: at its keyframe position and zoom (itself when it has none). */
    fun positioned(o: TextOverlay, timeMs: Long): TextOverlay {
        if (o.keyframes.isEmpty()) return o
        val l = interpolate(o.keyframes, timeMs - o.startMs)
        return o.copy(centerX = l.x, centerY = l.y, scale = (o.scale * l.zoom).coerceIn(0.1f, 20f), rotationDeg = o.rotationDeg + l.rotationDeg)
    }

    fun replace(list: List<OverlayKeyframe>, index: Int, with: OverlayKeyframe): List<OverlayKeyframe> =
        list.mapIndexed { i, k -> if (i == index) with.copy(offsetMs = k.offsetMs) else k }

    fun remove(list: List<OverlayKeyframe>, index: Int): List<OverlayKeyframe> = list.filterIndexed { i, _ -> i != index }
}
