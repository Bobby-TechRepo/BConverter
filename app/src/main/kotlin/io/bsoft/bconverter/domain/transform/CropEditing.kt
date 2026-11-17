package io.bsoft.bconverter.domain.transform

enum class CropEdge { LEFT, TOP, RIGHT, BOTTOM }

/** What the crop panel does to a [CropRect]: move one edge in or out, or pick a shape. */
object CropEditing {
    /** How much of the picture is cut away at [edge], 0 (nothing) up to 1 - [CropRect.MIN_SIDE]. */
    fun cut(c: CropRect, edge: CropEdge): Float = when (edge) {
        CropEdge.LEFT -> c.left
        CropEdge.TOP -> c.top
        CropEdge.RIGHT -> 1f - c.right
        CropEdge.BOTTOM -> 1f - c.bottom
    }

    /** Cuts [amount] away at [edge]; the edge stops when it would leave less than [CropRect.MIN_SIDE] of the picture. */
    fun withCut(c: CropRect, edge: CropEdge, amount: Float): CropRect {
        val a = if (amount.isFinite()) amount.coerceAtLeast(0f) else 0f
        return when (edge) {
            CropEdge.LEFT -> c.copy(left = a.coerceAtMost(c.right - CropRect.MIN_SIDE))
            CropEdge.TOP -> c.copy(top = a.coerceAtMost(c.bottom - CropRect.MIN_SIDE))
            CropEdge.RIGHT -> c.copy(right = (1f - a).coerceAtLeast(c.left + CropRect.MIN_SIDE).coerceAtMost(1f))
            CropEdge.BOTTOM -> c.copy(bottom = (1f - a).coerceAtLeast(c.top + CropRect.MIN_SIDE).coerceAtMost(1f))
        }
    }

    /** The shapes offered by the panel; a null ratio is the whole picture. */
    enum class Shape(val id: String, val aspect: Float?) {
        WHOLE("whole", null), SQUARE("1_1", 1f), WIDE("16_9", 16f / 9f), TALL("9_16", 9f / 16f), CLASSIC("4_3", 4f / 3f), PORTRAIT("3_4", 3f / 4f),
    }

    /**
     * A centred crop of [shape] as the *finished* picture should look. The crop is measured on the picture as shot, so when the clip
     * is turned a quarter the wanted shape is flipped before it is cut.
     */
    fun cropFor(shape: Shape, t: ClipTransform, sourceWidth: Int, sourceHeight: Int): CropRect {
        val aspect = shape.aspect ?: return CropRect.Full
        if (sourceWidth <= 0 || sourceHeight <= 0) return CropRect.Full
        return CropRect.centeredAspect(if (t.swapsSides) 1f / aspect else aspect, sourceWidth, sourceHeight)
    }

    /** The shape the crop already has (within a pixel or so), if it is one of the offered ones. */
    fun shapeOf(t: ClipTransform, sourceWidth: Int, sourceHeight: Int): Shape? {
        if (t.crop.isFull) return Shape.WHOLE
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        return Shape.entries.firstOrNull { s ->
            val c = cropFor(s, t, sourceWidth, sourceHeight)
            s != Shape.WHOLE && listOf(c.left - t.crop.left, c.top - t.crop.top, c.right - t.crop.right, c.bottom - t.crop.bottom).all { kotlin.math.abs(it) < 0.002f }
        }
    }
}
