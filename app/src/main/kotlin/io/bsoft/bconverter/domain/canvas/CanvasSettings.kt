package io.bsoft.bconverter.domain.canvas

/** The shape of the output picture. [AUTO] follows the first clip (as it looks after crop and rotation). */
enum class CanvasAspect(val ratio: Double?) {
    AUTO(null), WIDE(16.0 / 9), TALL(9.0 / 16), SQUARE(1.0), PORTRAIT(4.0 / 5),
}

/** How a clip that does not match the output shape is placed: [FIT] shows all of it (with bars), [FILL] covers the picture and crops the overflow. */
enum class CanvasFit { FIT, FILL }

/** What shows in the bars of a [CanvasFit.FIT] picture. */
sealed interface CanvasBackground {
    /** A plain colour, 0xRRGGBB. */
    data class Color(val rgb: Int) : CanvasBackground {
        init { require(rgb in 0..0xFFFFFF) { "rgb" } }
    }
    /** A blurred, enlarged copy of the picture itself. */
    data object Blur : CanvasBackground
}

/** The output shape and what fills it, for the whole project. The default changes nothing about how projects were exported before. */
data class CanvasSettings(
    val aspect: CanvasAspect = CanvasAspect.AUTO,
    val fit: CanvasFit = CanvasFit.FIT,
    val background: CanvasBackground = CanvasBackground.Color(0x000000),
) {
    val isDefault: Boolean get() = this == CanvasSettings()

    /**
     * The output size for a first clip of [width] × [height]: the short side of that picture is kept and the other side follows the
     * shape (1920×1080 as 9:16 is 1080×1920, as 1:1 is 1080×1080). Both sides are even. [CanvasAspect.AUTO] keeps the size as given.
     */
    fun sizeFor(width: Int, height: Int): Pair<Int, Int> {
        val ratio = aspect.ratio ?: return width to height
        val short = minOf(width, height).coerceAtLeast(2)
        val (w, h) = when {
            ratio > 1.0 -> (short * ratio) to short.toDouble()
            ratio < 1.0 -> short.toDouble() to (short / ratio)
            else -> short.toDouble() to short.toDouble()
        }
        return even(w) to even(h)
    }

    private fun even(v: Double): Int = ((Math.round(v / 2.0) * 2).toInt()).coerceAtLeast(2)
}
