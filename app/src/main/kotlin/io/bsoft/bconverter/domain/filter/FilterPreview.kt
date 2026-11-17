package io.bsoft.bconverter.domain.filter

/**
 * What the live preview can show of a [ClipFilter]: a colour grade built from the player's stock effects.
 * It is an approximation; blur, vignette, grain, sharpen, curves and invert appear only in the export.
 */
data class PreviewLook(
    val saturation: Float,
    val lightness: Float,
    val contrast: Float,
    val hueDegrees: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
)

object FilterPreview {
    /** The look to preview, or null when the preview should stay untouched. */
    fun of(f: ClipFilter): PreviewLook? {
        var saturation = f.saturation * 100f
        var red = 1f + 0.2f * f.temperature
        val green = 1f - 0.15f * f.tint
        var blue = 1f - 0.2f * f.temperature
        when (f.tone) {
            ToneMode.BLACK_WHITE -> saturation = -100f
            ToneMode.SEPIA -> { saturation = -100f; red *= 1.15f; blue *= 0.75f }
            ToneMode.NONE -> Unit
        }
        val look = PreviewLook(
            saturation = saturation.coerceIn(-100f, 100f),
            lightness = (f.brightness * 30f + f.exposure * 30f + f.fade * 8f + f.shadows * 8f + f.highlights * 8f).coerceIn(-100f, 100f),
            contrast = (f.contrast * 0.8f - f.fade * 0.25f + f.curve * 0.3f).coerceIn(-1f, 1f),
            hueDegrees = f.hue * 180f,
            red = red.coerceAtLeast(0f), green = green.coerceAtLeast(0f), blue = blue.coerceAtLeast(0f),
        )
        return if (look == NEUTRAL) null else look
    }

    /** True when part of the filter is visible only in the export. */
    fun hidesEffects(f: ClipFilter): Boolean = f.blur > 0f || f.vignette > 0f || f.grain > 0f || f.sharpen > 0f || f.invert

    private val NEUTRAL = PreviewLook(0f, 0f, 0f, 0f, 1f, 1f, 1f)
}
