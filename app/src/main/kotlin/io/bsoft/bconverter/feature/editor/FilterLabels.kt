package io.bsoft.bconverter.feature.editor

import androidx.annotation.StringRes
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.filter.FilterGroup
import io.bsoft.bconverter.domain.filter.FilterParam
import io.bsoft.bconverter.domain.filter.ToneMode

/** String resources for the filter panel (explicit, so a missing translation is a compile error rather than a blank label). */
object FilterLabels {
    @StringRes fun param(p: FilterParam): Int = when (p) {
        FilterParam.BRIGHTNESS -> R.string.filter_param_brightness
        FilterParam.CONTRAST -> R.string.filter_param_contrast
        FilterParam.SATURATION -> R.string.filter_param_saturation
        FilterParam.EXPOSURE -> R.string.filter_param_exposure
        FilterParam.TEMPERATURE -> R.string.filter_param_temperature
        FilterParam.TINT -> R.string.filter_param_tint
        FilterParam.HUE -> R.string.filter_param_hue
        FilterParam.SHARPEN -> R.string.filter_param_sharpen
        FilterParam.BLUR -> R.string.filter_param_blur
        FilterParam.VIGNETTE -> R.string.filter_param_vignette
        FilterParam.GRAIN -> R.string.filter_param_grain
        FilterParam.HIGHLIGHTS -> R.string.filter_param_highlights
        FilterParam.SHADOWS -> R.string.filter_param_shadows
        FilterParam.CURVE -> R.string.filter_param_curve
        FilterParam.FADE -> R.string.filter_param_fade
    }

    @StringRes fun group(g: FilterGroup): Int = when (g) {
        FilterGroup.BASIC -> R.string.filter_group_basic
        FilterGroup.DETAIL -> R.string.filter_group_detail
        FilterGroup.FADE_CURVES -> R.string.filter_group_fade_curves
    }

    @StringRes fun tone(t: ToneMode): Int = when (t) {
        ToneMode.NONE -> R.string.filter_tone_none
        ToneMode.BLACK_WHITE -> R.string.filter_tone_bw
        ToneMode.SEPIA -> R.string.filter_tone_sepia
    }

    @StringRes fun preset(id: String): Int = when (id) {
        "original" -> R.string.filter_preset_original
        "vivid" -> R.string.filter_preset_vivid
        "warm" -> R.string.filter_preset_warm
        "cool" -> R.string.filter_preset_cool
        "vintage" -> R.string.filter_preset_vintage
        "noir" -> R.string.filter_preset_noir
        "sepia" -> R.string.filter_preset_sepia
        "matte" -> R.string.filter_preset_matte
        "cinematic" -> R.string.filter_preset_cinematic
        "dramatic" -> R.string.filter_preset_dramatic
        "soft" -> R.string.filter_preset_soft
        "sharp" -> R.string.filter_preset_sharp
        "sunset" -> R.string.filter_preset_sunset
        "moody" -> R.string.filter_preset_moody
        "negative" -> R.string.filter_preset_negative
        else -> R.string.filter_preset_original
    }
}
