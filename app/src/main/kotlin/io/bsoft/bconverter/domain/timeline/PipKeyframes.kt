package io.bsoft.bconverter.domain.timeline

/** How an overlay clip looks at one moment: its centre and width (shares of the frame) and its opacity. */
data class PipState(val x: Float, val y: Float, val width: Float, val opacity: Float)

/**
 * One point of an overlay clip's animation: [offsetMs] after the clip starts on the timeline, it is centred at ([x], [y]) with
 * [width] and [opacity]. Between two keyframes everything changes in a straight line (or, when [eased], starting and ending slowly); before the first and after the last it holds still.
 */
data class PipKeyframe(val offsetMs: Long, val x: Float, val y: Float, val width: Float, val opacity: Float, val eased: Boolean = false) {
    init {
        require(offsetMs >= 0) { "offset must be >= 0" }
        require(x.isFinite() && x in 0f..1f && y.isFinite() && y in 0f..1f) { "centre must be inside the frame" }
        require(width.isFinite() && width in PipLayout.MIN_WIDTH..PipLayout.MAX_WIDTH) { "width must be within the size limits" }
        require(opacity.isFinite() && opacity in 0f..1f) { "opacity must be within 0..1" }
    }
    val state: PipState get() = PipState(x, y, width, opacity)
}

object PipKeyframes {
    const val MAX = 16

    /** Strictly increasing offsets, at most [MAX], all within the clip (0..[durationMs]). */
    fun isValid(list: List<PipKeyframe>, durationMs: Long): Boolean =
        list.size <= MAX && list.zipWithNext().all { (a, b) -> a.offsetMs < b.offsetMs } && list.all { it.offsetMs in 0..durationMs }

    /** Keeps the keyframes that still fall within a clip of [durationMs] (after a trim or a speed change). */
    fun fitted(list: List<PipKeyframe>, durationMs: Long): List<PipKeyframe> = list.filter { it.offsetMs <= durationMs }

    fun interpolate(list: List<PipKeyframe>, offsetMs: Long): PipState {
        require(list.isNotEmpty()) { "no keyframes" }
        if (offsetMs <= list.first().offsetMs) return list.first().state
        if (offsetMs >= list.last().offsetMs) return list.last().state
        val i = list.indexOfLast { it.offsetMs <= offsetMs }
        val a = list[i]; val b = list[i + 1]
        val raw = (offsetMs - a.offsetMs).toFloat() / (b.offsetMs - a.offsetMs)
        val f = if (a.eased) raw * raw * (3 - 2 * raw) else raw
        fun mix(p: Float, q: Float) = p + (q - p) * f
        return PipState(mix(a.x, b.x), mix(a.y, b.y), mix(a.width, b.width), mix(a.opacity, b.opacity))
    }

    /**
     * The keyframes after adding one at [offsetMs] on the clip's timeline (taking the look the clip has there). The first one also
     * turns the clip's present look (its glide, if any) into keyframes, so nothing jumps. Unchanged if the moment is outside the clip,
     * already has a keyframe, or there are too many.
     */
    fun addAt(clip: PipClip, offsetMs: Long): List<PipKeyframe> {
        val now = clip.keyframes
        if (offsetMs !in 0..clip.durationMs) return now
        val base = now.ifEmpty {
            val l = clip.layout
            val start = PipKeyframe(0, l.centerX, l.centerY, l.width, l.opacity)
            val m = clip.motion
            if (m == null) listOf(start) else listOf(start, PipKeyframe(clip.durationMs, m.endX, m.endY, m.endWidth ?: l.width, l.opacity))
        }
        if (base.any { it.offsetMs == offsetMs } || base.size >= MAX) return base
        val s = interpolate(base, offsetMs)
        return (base + PipKeyframe(offsetMs, s.x, s.y, s.width, s.opacity)).sortedBy { it.offsetMs }
    }

    /** A keyframe this close (ms) to a moment counts as being at it, so a drag at the playhead keeps editing the same one. */
    const val SNAP_MS = 50L

    /**
     * The keyframes after the clip is given [look] at [offsetMs]: a keyframe within [SNAP_MS] of the moment takes the look (keeping its
     * time and easing), otherwise a new one is added there. Unchanged when the moment is outside the clip or there are too many.
     */
    fun withLookAt(clip: PipClip, offsetMs: Long, look: PipState): List<PipKeyframe> {
        val now = clip.keyframes
        if (offsetMs !in 0..clip.durationMs) return now
        fun frame(t: Long, eased: Boolean) = PipKeyframe(
            t, look.x.coerceIn(0f, 1f), look.y.coerceIn(0f, 1f), look.width.coerceIn(PipLayout.MIN_WIDTH, PipLayout.MAX_WIDTH), look.opacity.coerceIn(0f, 1f), eased,
        )
        val near = now.indexOfFirst { kotlin.math.abs(it.offsetMs - offsetMs) <= SNAP_MS }
        if (near >= 0) return now.mapIndexed { i, k -> if (i == near) frame(k.offsetMs, k.eased) else k }
        if (now.size >= MAX) return now
        return (now + frame(offsetMs, false)).sortedBy { it.offsetMs }
    }

    fun replace(list: List<PipKeyframe>, index: Int, with: PipKeyframe): List<PipKeyframe> =
        list.mapIndexed { i, k -> if (i == index) with.copy(offsetMs = k.offsetMs) else k }

    fun remove(list: List<PipKeyframe>, index: Int): List<PipKeyframe> = list.filterIndexed { i, _ -> i != index }
}
