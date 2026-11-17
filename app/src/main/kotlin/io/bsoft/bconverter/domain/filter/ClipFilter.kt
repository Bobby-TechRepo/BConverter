package io.bsoft.bconverter.domain.filter

/** Black and white or sepia replaces the colours; [NONE] keeps them. */
enum class ToneMode { NONE, BLACK_WHITE, SEPIA }

/**
 * The look of one video clip. Every slider is normalised: signed ones run -1..1 (0 = unchanged), unsigned ones 0..1.
 * [presetId] only remembers which preset this started from; editing any value forgets it.
 */
data class ClipFilter(
    // Basic grade
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val hue: Float = 0f,
    // Detail and effects
    val sharpen: Float = 0f,
    val blur: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    // Fade and curves
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val curve: Float = 0f,
    val fade: Float = 0f,
    // Toggles
    val tone: ToneMode = ToneMode.NONE,
    val invert: Boolean = false,
    val presetId: String? = null,
) {
    init {
        for (p in FilterParam.entries) {
            val v = p.get(this)
            require(v.isFinite() && v >= p.min && v <= p.max) { "${p.key} must be within ${p.min}..${p.max}" }
        }
    }

    /** True when exporting with this filter changes nothing. */
    val isIdentity: Boolean get() = copy(presetId = null) == None

    fun reset(): ClipFilter = None

    companion object { val None = ClipFilter() }
}

enum class FilterGroup { BASIC, DETAIL, FADE_CURVES }

/** One slider: where it lives, its range and how to read/write it on a [ClipFilter]. */
enum class FilterParam(val key: String, val group: FilterGroup, val min: Float, val max: Float, val default: Float = 0f) {
    BRIGHTNESS("brightness", FilterGroup.BASIC, -1f, 1f),
    CONTRAST("contrast", FilterGroup.BASIC, -1f, 1f),
    SATURATION("saturation", FilterGroup.BASIC, -1f, 1f),
    EXPOSURE("exposure", FilterGroup.BASIC, -1f, 1f),
    TEMPERATURE("temperature", FilterGroup.BASIC, -1f, 1f),
    TINT("tint", FilterGroup.BASIC, -1f, 1f),
    HUE("hue", FilterGroup.BASIC, -1f, 1f),
    SHARPEN("sharpen", FilterGroup.DETAIL, 0f, 1f),
    BLUR("blur", FilterGroup.DETAIL, 0f, 1f),
    VIGNETTE("vignette", FilterGroup.DETAIL, 0f, 1f),
    GRAIN("grain", FilterGroup.DETAIL, 0f, 1f),
    HIGHLIGHTS("highlights", FilterGroup.FADE_CURVES, -1f, 1f),
    SHADOWS("shadows", FilterGroup.FADE_CURVES, -1f, 1f),
    CURVE("curve", FilterGroup.FADE_CURVES, -1f, 1f),
    FADE("fade", FilterGroup.FADE_CURVES, 0f, 1f);

    val isSigned: Boolean get() = min < 0f

    fun get(f: ClipFilter): Float = when (this) {
        BRIGHTNESS -> f.brightness; CONTRAST -> f.contrast; SATURATION -> f.saturation; EXPOSURE -> f.exposure
        TEMPERATURE -> f.temperature; TINT -> f.tint; HUE -> f.hue
        SHARPEN -> f.sharpen; BLUR -> f.blur; VIGNETTE -> f.vignette; GRAIN -> f.grain
        HIGHLIGHTS -> f.highlights; SHADOWS -> f.shadows; CURVE -> f.curve; FADE -> f.fade
    }

    /** Returns [f] with this slider at [value] (clamped to the range); the preset name is forgotten. */
    fun set(f: ClipFilter, value: Float): ClipFilter {
        val v = value.coerceIn(min, max)
        val g = f.copy(presetId = null)
        return when (this) {
            BRIGHTNESS -> g.copy(brightness = v); CONTRAST -> g.copy(contrast = v); SATURATION -> g.copy(saturation = v)
            EXPOSURE -> g.copy(exposure = v); TEMPERATURE -> g.copy(temperature = v); TINT -> g.copy(tint = v); HUE -> g.copy(hue = v)
            SHARPEN -> g.copy(sharpen = v); BLUR -> g.copy(blur = v); VIGNETTE -> g.copy(vignette = v); GRAIN -> g.copy(grain = v)
            HIGHLIGHTS -> g.copy(highlights = v); SHADOWS -> g.copy(shadows = v); CURVE -> g.copy(curve = v); FADE -> g.copy(fade = v)
        }
    }
}
