package io.bsoft.bconverter.domain.transform

/** The part of the frame that is kept, as fractions of the (upright) source picture: 0 = left/top edge, 1 = right/bottom edge. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) { "crop must be finite" }
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) { "crop must stay inside the frame" }
        require(right - left >= MIN_SIDE - EPS && bottom - top >= MIN_SIDE - EPS) { "crop must keep at least $MIN_SIDE of each side" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left == 0f && top == 0f && right == 1f && bottom == 1f

    companion object {
        const val MIN_SIDE = 0.1f
        private const val EPS = 1e-4f
        val Full = CropRect()

        /** A crop whose pixel shape is [aspect] (width / height) of a [frameWidth] × [frameHeight] picture, centred and as large as fits. */
        fun centeredAspect(aspect: Float, frameWidth: Int, frameHeight: Int): CropRect {
            require(aspect > 0f && frameWidth > 0 && frameHeight > 0)
            val frameAspect = frameWidth.toFloat() / frameHeight
            return if (aspect >= frameAspect) {
                val h = frameAspect / aspect
                CropRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
            } else {
                val w = aspect / frameAspect
                CropRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
            }
        }
    }
}

/**
 * How one clip's picture is cut and turned. Order of operations: crop (in the upright source), then rotate clockwise,
 * then mirror (in the rotated picture).
 */
data class ClipTransform(
    val crop: CropRect = CropRect.Full,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
) {
    init { require(rotationDegrees in setOf(0, 90, 180, 270)) { "rotation must be 0, 90, 180 or 270" } }

    val isIdentity: Boolean get() = this == None
    val swapsSides: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270

    fun rotatedRight() = copy(rotationDegrees = (rotationDegrees + 90) % 360)
    fun rotatedLeft() = copy(rotationDegrees = (rotationDegrees + 270) % 360)
    fun flippedHorizontally() = copy(flipHorizontal = !flipHorizontal)
    fun flippedVertically() = copy(flipVertical = !flipVertical)
    fun withCrop(c: CropRect) = copy(crop = c)

    /** The size of the picture after the transform, for an upright source of [width] × [height]; the same even-pixel rounding the export uses. */
    fun outputSize(width: Int, height: Int): Pair<Int, Int> {
        val w = croppedSide(width, crop.left, crop.right)
        val h = croppedSide(height, crop.top, crop.bottom)
        return if (swapsSides) h to w else w to h
    }

    companion object {
        val None = ClipTransform()

        /** Pixels kept along one axis: the cropped length rounded down to an even number (never below 2). */
        fun croppedSide(length: Int, from: Float, to: Float): Int {
            if (from == 0f && to == 1f) return length
            val kept = Math.floor(length * (to - from).toDouble() / 2.0).toInt() * 2
            return kept.coerceIn(2, length)
        }

        /** The even pixel offset of the crop's start edge. */
        fun cropOffset(length: Int, from: Float): Int = (Math.floor(length * from.toDouble() / 2.0).toInt() * 2).coerceAtLeast(0)
    }
}
