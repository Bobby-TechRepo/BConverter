package io.bsoft.bconverter.domain.overlay

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** The rough size an overlay's text box takes on a frame; used to find what a touch hit (the real text is measured when drawn). */
data class OverlayBox(val widthPx: Float, val heightPx: Float)

object OverlayBounds {
    private const val AVERAGE_GLYPH_WIDTH = 0.56f // of the font size, for a typical proportional font
    private const val LINE_HEIGHT = 1.2f // of the font size

    fun estimate(o: TextOverlay, frameWidth: Int, frameHeight: Int): OverlayBox {
        if (o.isImage) return OverlayGeometry.imageSizePx(o, frameWidth, frameHeight).let { OverlayBox(it.first, it.second) }
        val px = OverlayGeometry.toPixels(o, frameWidth, frameHeight)
        val lines = o.text.split('\n').ifEmpty { listOf("") }
        val natural = lines.maxOf { it.length } * px.fontSizePx * (AVERAGE_GLYPH_WIDTH + o.style.letterSpacing)
        val width = px.boxWidthPx?.coerceAtMost(max(natural, px.fontSizePx)) ?: natural
        val wrapped = if (px.boxWidthPx != null && natural > px.boxWidthPx) kotlin.math.ceil(natural / px.boxWidthPx).toInt() else 1
        val height = lines.size * wrapped * px.fontSizePx * LINE_HEIGHT * o.style.lineSpacing
        val pad = (o.style.background?.padding ?: 0f) * px.fontSizePx * 2
        return OverlayBox(max(width, px.fontSizePx) + pad, max(height, px.fontSizePx) + pad)
    }
}

object OverlayHitTest {
    /** Extra reach so thin text is still easy to touch, in pixels. */
    const val TOUCH_SLOP_PX = 24f

    /** The id of the topmost overlay shown at [timeMs] under the point ([x], [y]) (frame pixels), or null. */
    fun topmostAt(overlays: List<TextOverlay>, timeMs: Long, x: Float, y: Float, frameWidth: Int, frameHeight: Int): String? =
        overlays.lastOrNull { o -> o.isShownAt(timeMs) && hits(OverlayKeyframes.positioned(o, timeMs), x, y, frameWidth, frameHeight) }?.id

    fun hits(o: TextOverlay, x: Float, y: Float, frameWidth: Int, frameHeight: Int): Boolean {
        val p = OverlayGeometry.toPixels(o, frameWidth, frameHeight)
        val box = OverlayBounds.estimate(o, frameWidth, frameHeight)
        val rad = Math.toRadians(-o.rotationDeg.toDouble()) // undo the overlay's rotation so the box test is axis-aligned
        val dx = x - p.centerX; val dy = y - p.centerY
        val lx = (dx * cos(rad) - dy * sin(rad)).toFloat(); val ly = (dx * sin(rad) + dy * cos(rad)).toFloat()
        return abs(lx) <= box.widthPx / 2 + TOUCH_SLOP_PX && abs(ly) <= box.heightPx / 2 + TOUCH_SLOP_PX
    }
}

object OverlaySnap {
    const val CENTER_TOLERANCE = 0.015f
    const val ANGLE_TOLERANCE_DEG = 3f

    /** Snaps a 0..1 position to the middle of the frame when it is close. */
    fun position(v: Float): Float = if (abs(v - 0.5f) <= CENTER_TOLERANCE) 0.5f else v

    /** Snaps an angle to the nearest multiple of 90° when it is close. */
    fun rotation(deg: Float): Float {
        val nearest = (deg / 90f).roundToInt() * 90f
        return if (abs(deg - nearest) <= ANGLE_TOLERANCE_DEG) nearest else deg
    }
}

data class GestureResult(val overlay: TextOverlay, val snappedX: Boolean, val snappedY: Boolean, val snappedAngle: Boolean)

/**
 * One drag/pinch/rotate gesture on an overlay. The raw finger movement is accumulated here and only the displayed result is
 * snapped, so the overlay does not stick at a guide: moving further simply leaves it. Positions stay inside the frame,
 * scale within 0.1..20, rotation within (-180, 180].
 */
class OverlayGestureSession(start: TextOverlay) {
    private val base = start
    private var x = start.centerX
    private var y = start.centerY
    private var scale = start.scale
    private var rotation = start.rotationDeg

    /** [dx]/[dy] are fractions of the frame width/height moved since the last call; [zoom] a factor; [rotationDeg] the change. */
    fun update(dx: Float, dy: Float, zoom: Float, rotationDeg: Float): GestureResult {
        if (dx.isFinite()) x = (x + dx).coerceIn(0f, 1f)
        if (dy.isFinite()) y = (y + dy).coerceIn(0f, 1f)
        if (zoom.isFinite() && zoom > 0f) scale = (scale * zoom).coerceIn(0.1f, 20f)
        if (rotationDeg.isFinite()) rotation += rotationDeg
        val sx = OverlaySnap.position(x); val sy = OverlaySnap.position(y)
        val angle = normalise(rotation).let { OverlaySnap.rotation(it) }
        return GestureResult(base.copy(centerX = sx, centerY = sy, scale = scale, rotationDeg = normalise(angle)), sx != x, sy != y, angle != normalise(rotation))
    }

    companion object {
        fun normalise(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }
    }
}

/** Packs overlays into rows so that ones overlapping in time are not drawn on top of each other in the timeline. */
object OverlayLanes {
    /** Row index for every overlay id: the lowest row whose previous overlay has ended. */
    fun assign(overlays: List<TextOverlay>): Map<String, Int> {
        val rowEnds = mutableListOf<Long>()
        val result = LinkedHashMap<String, Int>()
        overlays.sortedWith(compareBy({ it.startMs }, { it.endMs })).forEach { o ->
            val row = rowEnds.indexOfFirst { it <= o.startMs }.let { if (it >= 0) it else { rowEnds.add(0L); rowEnds.lastIndex } }
            rowEnds[row] = o.endMs
            result[o.id] = row
        }
        return result
    }

    fun rowCount(overlays: List<TextOverlay>): Int = (assign(overlays).values.maxOrNull() ?: -1) + 1
}

/** Reading numeric fields typed by the user. All return null when the text is not a valid value in range. */
object OverlayNumericInput {
    /** "50", "50%", "12,5" → a fraction 0.5, 0.5, 0.125; the value must be within [minPercent]..[maxPercent]. */
    fun percent(text: String, minPercent: Float = 0f, maxPercent: Float = 100f): Float? {
        val v = number(text.trim().removeSuffix("%")) ?: return null
        return if (v in minPercent..maxPercent) v / 100f else null
    }

    fun degrees(text: String): Float? = number(text.trim().removeSuffix("°"))?.takeIf { abs(it) <= 100_000f }

    fun number(text: String): Float? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() || c == '.' || c == '-' || c == '+' } }
        ?.toFloatOrNull()?.takeIf { it.isFinite() }

    /** A fraction as a short percent text without trailing zeros: 0.5 → "50", 0.125 → "12.5". */
    fun percentText(fraction: Float): String = trim(fraction * 100f)

    fun numberText(v: Float): String = trim(v)

    private fun trim(v: Float): String {
        val s = String.format(java.util.Locale.ROOT, "%.1f", v)
        return s.removeSuffix(".0")
    }
}

object TextOverlayDefaults {
    const val DEFAULT_DURATION_MS = 3_000L

    /** A picture at the middle of the frame, starting at [startMs]. */
    fun newImageAt(id: String, startMs: Long, image: OverlayImage): TextOverlay = TextOverlay(
        id = id, text = "", startMs = startMs.coerceAtLeast(0), endMs = startMs.coerceAtLeast(0) + DEFAULT_DURATION_MS, image = image,
    )

    /** A readable caption (bold, white with a dark outline) at the middle of the picture, starting at [startMs]. */
    fun newAt(id: String, startMs: Long, text: String = "Text"): TextOverlay = TextOverlay(
        id = id, text = text, startMs = startMs.coerceAtLeast(0), endMs = startMs.coerceAtLeast(0) + DEFAULT_DURATION_MS,
        style = TextOverlayStyle(fontWeight = 700, outline = OverlayOutline()),
    )
}
