package io.bsoft.bconverter.domain.model

/**
 * How large an exported project is. [AUTO] follows the quality (the small-file quality is capped at 720p, the others keep the
 * project's size); [ORIGINAL] always keeps it; the rest cap the shorter side at [shortSide] pixels, keeping the shape.
 * A smaller project is never made larger.
 */
enum class ExportSize(val shortSide: Int?) {
    AUTO(null), ORIGINAL(null), P1080(1080), P720(720), P480(480);

    /** The size to export at for a project of [reference] size, or null to keep it as it is. */
    fun cap(reference: Resolution?): Resolution? {
        val target = shortSide ?: return null
        if (reference == null) return null
        val short = minOf(reference.width, reference.height)
        if (short <= target) return null
        val scale = target.toDouble() / short
        fun even(v: Double) = ((Math.round(v / 2.0) * 2).toInt()).coerceAtLeast(2)
        return Resolution(even(reference.width * scale), even(reference.height * scale))
    }
}
