package io.bsoft.bconverter.domain.overlay

enum class OverlayProblem { NoSuchOverlay, DuplicateId, InvalidTiming, InvalidValue }

sealed interface OverlayEdit {
    data class Ok(val overlays: List<TextOverlay>) : OverlayEdit
    data class Rejected(val reason: OverlayProblem) : OverlayEdit
}

/** Pure edits of the overlay list. The list order is the layer order: index 0 is at the back, the last item is on top. */
object TextOverlayEditor {
    private fun ok(l: List<TextOverlay>) = OverlayEdit.Ok(l)
    private fun no(p: OverlayProblem) = OverlayEdit.Rejected(p)
    private fun List<TextOverlay>.has(id: String) = any { it.id == id }

    private inline fun change(list: List<TextOverlay>, id: String, f: (TextOverlay) -> TextOverlay): OverlayEdit {
        if (!list.has(id)) return no(OverlayProblem.NoSuchOverlay)
        return try { ok(list.map { if (it.id == id) f(it) else it }) } catch (e: IllegalArgumentException) { no(OverlayProblem.InvalidValue) }
    }

    /** A new overlay goes on top of the others. */
    fun add(list: List<TextOverlay>, overlay: TextOverlay): OverlayEdit =
        if (list.has(overlay.id)) no(OverlayProblem.DuplicateId) else ok(list + overlay)

    fun remove(list: List<TextOverlay>, id: String): OverlayEdit =
        if (!list.has(id)) no(OverlayProblem.NoSuchOverlay) else ok(list.filterNot { it.id == id })

    fun setText(list: List<TextOverlay>, id: String, text: String) = change(list, id) { it.copy(text = text) }

    fun rename(list: List<TextOverlay>, id: String, name: String?) = change(list, id) { it.copy(name = name?.takeIf(String::isNotBlank)) }

    /** Puts the centre at ([x], [y]); each is kept within the frame (0..1) so an overlay cannot be lost off screen. */
    fun moveTo(list: List<TextOverlay>, id: String, x: Float, y: Float) =
        if (!x.isFinite() || !y.isFinite()) no(OverlayProblem.InvalidValue) else change(list, id) { it.copy(centerX = x.coerceIn(0f, 1f), centerY = y.coerceIn(0f, 1f)) }

    /** Moves by a fraction of the frame (what a drag gesture produces). */
    fun nudge(list: List<TextOverlay>, id: String, dx: Float, dy: Float): OverlayEdit {
        val o = list.firstOrNull { it.id == id } ?: return no(OverlayProblem.NoSuchOverlay)
        return moveTo(list, id, o.centerX + dx, o.centerY + dy)
    }

    /** The angle is stored in the range (-180, 180]. */
    fun setRotation(list: List<TextOverlay>, id: String, degrees: Float): OverlayEdit {
        if (!degrees.isFinite()) return no(OverlayProblem.InvalidValue)
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return change(list, id) { it.copy(rotationDeg = d) }
    }

    /** Multiplies the scale (pinch gesture); the result stays within 0.1..20. */
    fun scaleBy(list: List<TextOverlay>, id: String, factor: Float): OverlayEdit {
        val o = list.firstOrNull { it.id == id } ?: return no(OverlayProblem.NoSuchOverlay)
        if (!factor.isFinite() || factor <= 0f) return no(OverlayProblem.InvalidValue)
        return change(list, id) { it.copy(scale = (o.scale * factor).coerceIn(0.1f, 20f)) }
    }

    fun setScale(list: List<TextOverlay>, id: String, scale: Float) = change(list, id) { it.copy(scale = scale) }

    fun setWidth(list: List<TextOverlay>, id: String, widthFraction: Float?) = change(list, id) { it.copy(widthFraction = widthFraction) }

    fun retime(list: List<TextOverlay>, id: String, startMs: Long, endMs: Long): OverlayEdit {
        if (!list.has(id)) return no(OverlayProblem.NoSuchOverlay)
        if (startMs < 0 || endMs <= startMs) return no(OverlayProblem.InvalidTiming)
        return change(list, id) { it.copy(startMs = startMs, endMs = endMs, keyframes = OverlayKeyframes.fitted(it.keyframes, endMs - startMs)) }
    }

    /** Shifts the whole overlay in time, keeping its length; it stops at time zero. */
    fun shift(list: List<TextOverlay>, id: String, deltaMs: Long): OverlayEdit {
        val o = list.firstOrNull { it.id == id } ?: return no(OverlayProblem.NoSuchOverlay)
        val start = (o.startMs + deltaMs).coerceAtLeast(0)
        return change(list, id) { it.copy(startMs = start, endMs = start + o.durationMs) }
    }

    fun setStyle(list: List<TextOverlay>, id: String, style: TextOverlayStyle) = change(list, id) { it.copy(style = style) }

    /** Applies [f] to the current style; an out-of-range result is rejected as InvalidValue. */
    fun updateStyle(list: List<TextOverlay>, id: String, f: (TextOverlayStyle) -> TextOverlayStyle) = change(list, id) { it.copy(style = f(it.style)) }

    fun setAnimations(list: List<TextOverlay>, id: String, animIn: OverlayAnimation?, animOut: OverlayAnimation?) =
        change(list, id) { it.copy(animIn = animIn, animOut = animOut) }

    /** Sets the keyframe animation; keyframes must be in increasing order, inside the overlay's time. */
    fun setKeyframes(list: List<TextOverlay>, id: String, keyframes: List<OverlayKeyframe>): OverlayEdit {
        val o = list.firstOrNull { it.id == id } ?: return no(OverlayProblem.NoSuchOverlay)
        if (keyframes.any { it.offsetMs > o.durationMs }) return no(OverlayProblem.InvalidValue)
        return change(list, id) { it.copy(keyframes = keyframes) }
    }

    /** The copy sits on top, starts where the original ends, and is nudged slightly so it is visible as a separate item. */
    fun duplicate(list: List<TextOverlay>, id: String, newId: String): OverlayEdit {
        val o = list.firstOrNull { it.id == id } ?: return no(OverlayProblem.NoSuchOverlay)
        if (list.has(newId)) return no(OverlayProblem.DuplicateId)
        return ok(list + o.copy(id = newId, startMs = o.endMs, endMs = o.endMs + o.durationMs, centerX = (o.centerX + 0.03f).coerceAtMost(1f), centerY = (o.centerY + 0.03f).coerceAtMost(1f)))
    }

    // ---- layers ----
    fun bringToFront(list: List<TextOverlay>, id: String) = reorder(list, id) { _, size -> size - 1 }
    fun sendToBack(list: List<TextOverlay>, id: String) = reorder(list, id) { _, _ -> 0 }
    fun bringForward(list: List<TextOverlay>, id: String) = reorder(list, id) { i, size -> (i + 1).coerceAtMost(size - 1) }
    fun sendBackward(list: List<TextOverlay>, id: String) = reorder(list, id) { i, _ -> (i - 1).coerceAtLeast(0) }

    private inline fun reorder(list: List<TextOverlay>, id: String, target: (Int, Int) -> Int): OverlayEdit {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return no(OverlayProblem.NoSuchOverlay)
        val moving = list[i]
        val rest = list.toMutableList().also { it.removeAt(i) }
        return ok(rest.also { it.add(target(i, list.size), moving) })
    }

    /** Overlays visible at [timeMs], back to front. */
    fun visibleAt(list: List<TextOverlay>, timeMs: Long): List<TextOverlay> = list.filter { it.isShownAt(timeMs) }
}
