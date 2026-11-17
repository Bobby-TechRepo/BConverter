package io.bsoft.bconverter.domain.filter

/** A named starting look. The display name is the string resource [nameKey]. */
data class FilterPreset(val id: String, val filter: ClipFilter) {
    val nameKey: String get() = "filter_preset_$id"
}

object FilterPresets {
    private fun p(id: String, build: ClipFilter.() -> ClipFilter) = FilterPreset(id, ClipFilter.None.build().copy(presetId = id))

    val all: List<FilterPreset> = listOf(
        FilterPreset("original", ClipFilter.None),
        p("vivid") { copy(contrast = 0.15f, saturation = 0.45f, sharpen = 0.2f) },
        p("warm") { copy(temperature = 0.45f, tint = 0.05f, saturation = 0.1f, brightness = 0.03f) },
        p("cool") { copy(temperature = -0.45f, saturation = 0.05f, contrast = 0.05f) },
        p("vintage") { copy(temperature = 0.3f, saturation = -0.25f, fade = 0.3f, vignette = 0.35f, grain = 0.25f, contrast = -0.05f) },
        p("noir") { copy(tone = ToneMode.BLACK_WHITE, contrast = 0.3f, vignette = 0.4f, curve = 0.3f) },
        p("sepia") { copy(tone = ToneMode.SEPIA, vignette = 0.2f, fade = 0.1f) },
        p("matte") { copy(fade = 0.45f, saturation = -0.2f, contrast = -0.1f, shadows = 0.1f) },
        p("cinematic") { copy(contrast = 0.2f, saturation = -0.1f, temperature = 0.15f, tint = -0.15f, vignette = 0.3f, curve = 0.35f) },
        p("dramatic") { copy(contrast = 0.4f, saturation = -0.2f, shadows = -0.25f, highlights = 0.1f, vignette = 0.45f, sharpen = 0.3f) },
        p("soft") { copy(blur = 0.12f, brightness = 0.05f, contrast = -0.15f, saturation = -0.05f, highlights = -0.1f) },
        p("sharp") { copy(sharpen = 0.7f, contrast = 0.1f) },
        p("sunset") { copy(temperature = 0.6f, tint = 0.15f, saturation = 0.25f, exposure = -0.05f, vignette = 0.15f) },
        p("moody") { copy(exposure = -0.2f, contrast = 0.2f, saturation = -0.3f, temperature = -0.2f, vignette = 0.5f) },
        p("negative") { copy(invert = true) },
    )

    fun byId(id: String): FilterPreset? = all.firstOrNull { it.id == id }
}
