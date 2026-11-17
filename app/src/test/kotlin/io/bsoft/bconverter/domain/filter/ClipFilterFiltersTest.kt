package io.bsoft.bconverter.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipFilterFiltersTest {
    private fun f(filter: ClipFilter, h: Int = 1000) = ClipFilterFilters.build(filter, h)

    @Test fun the_identity_adds_no_filters() { assertTrue(f(ClipFilter.None).isEmpty()) }
    @Test fun a_preset_name_alone_adds_nothing() { assertTrue(f(ClipFilter(presetId = "x")).isEmpty()) }

    @Test fun brightness_contrast_and_exposure_share_one_luma_table() {
        assertEquals(listOf("lutyuv=y='clip(val/maxval+0.15,0,1)*maxval'"), f(ClipFilter(brightness = 0.5f)))
        assertEquals(listOf("lutyuv=y='clip(val/maxval-0.15,0,1)*maxval'"), f(ClipFilter(brightness = -0.5f)))
        assertEquals(listOf("lutyuv=y='clip((val/maxval-0.5)*1.4+0.5,0,1)*maxval'"), f(ClipFilter(contrast = 0.5f)))
        assertEquals(listOf("lutyuv=y='clip(pow(val/maxval,0.5),0,1)*maxval'"), f(ClipFilter(exposure = 1f)))
        assertEquals(listOf("lutyuv=y='clip(pow(val/maxval,2),0,1)*maxval'"), f(ClipFilter(exposure = -1f)))
        assertEquals(listOf("lutyuv=y='clip((pow(val/maxval,0.5)-0.5)*1.4+0.5+0.15,0,1)*maxval'"),
            f(ClipFilter(brightness = 0.5f, contrast = 0.5f, exposure = 1f)))
    }
    @Test fun saturation_is_a_multiplier_on_the_hue_filter() {
        assertEquals(listOf("hue=s=1.5"), f(ClipFilter(saturation = 0.5f)))
        assertEquals(listOf("hue=s=0"), f(ClipFilter(saturation = -1f)))
    }
    @Test fun warm_adds_red_and_removes_blue_in_every_range() {
        assertEquals(listOf("colorbalance=rs=0.25:gs=0:bs=-0.25:rm=0.25:gm=0:bm=-0.25:rh=0.25:gh=0:bh=-0.25"), f(ClipFilter(temperature = 1f)))
    }
    @Test fun a_positive_tint_goes_magenta() {
        assertEquals(listOf("colorbalance=rs=0:gs=-0.25:bs=0:rm=0:gm=-0.25:bm=0:rh=0:gh=-0.25:bh=0"), f(ClipFilter(tint = 1f)))
    }
    @Test fun hue_is_degrees() {
        assertEquals(listOf("hue=h=90"), f(ClipFilter(hue = 0.5f)))
        assertEquals(listOf("hue=h=-180"), f(ClipFilter(hue = -1f)))
    }
    @Test fun hue_and_saturation_share_one_filter() {
        assertEquals(listOf("hue=h=90:s=1.5"), f(ClipFilter(hue = 0.5f, saturation = 0.5f)))
    }
    @Test fun only_filters_that_exist_on_the_device_are_ever_used() {
        val everything = FilterPresets.all.map { it.filter } + FilterParam.entries.flatMap { p -> listOf(p.max, p.min).map { p.set(ClipFilter.None, it) } } +
            ClipFilter(tone = ToneMode.SEPIA, invert = true, hue = 0.3f, saturation = 0.2f)
        for (c in everything) for (s in f(c)) {
            val name = s.substringBefore('=')
            assertTrue("$name is not in ClipFilterFilters.USED_FILTERS", name in ClipFilterFilters.USED_FILTERS)
        }
    }
    @Test fun shadows_and_highlights_move_the_curve_points() {
        assertEquals(listOf("curves=all='0/0 0.25/0.4 0.75/0.75 1/1'"), f(ClipFilter(shadows = 1f)))
        assertEquals(listOf("curves=all='0/0 0.25/0.25 0.75/0.6 1/1'"), f(ClipFilter(highlights = -1f)))
    }
    @Test fun the_tone_curve_is_an_s_curve() {
        assertEquals(listOf("curves=all='0/0 0.25/0.15 0.75/0.85 1/1'"), f(ClipFilter(curve = 1f)))
    }
    @Test fun fade_lifts_the_blacks_and_squeezes_the_range() {
        assertEquals(listOf("curves=all='0/0.25 0.25/0.4375 0.75/0.8125 1/1'"), f(ClipFilter(fade = 1f)))
    }
    @Test fun curve_points_stay_within_zero_and_one() {
        val c = f(ClipFilter(shadows = -1f, curve = 1f, fade = 0f)).single()
        assertTrue(c, !c.contains("-"))
    }
    @Test fun black_and_white_sepia_and_invert() {
        assertEquals(listOf("hue=s=0"), f(ClipFilter(tone = ToneMode.BLACK_WHITE)))
        assertEquals(listOf("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131:0"), f(ClipFilter(tone = ToneMode.SEPIA)))
        assertEquals(listOf("negate"), f(ClipFilter(invert = true)))
    }
    @Test fun sharpen_is_an_unsharp_mask() {
        assertEquals(listOf("unsharp=5:5:1.5:5:5:0"), f(ClipFilter(sharpen = 1f)))
    }
    @Test fun blur_scales_with_the_frame_height() {
        assertEquals(listOf("gblur=sigma=12"), f(ClipFilter(blur = 1f), h = 1000))
        assertEquals(listOf("gblur=sigma=6"), f(ClipFilter(blur = 1f), h = 500))
    }
    @Test fun vignette_gets_stronger_as_the_angle_closes() {
        assertEquals(listOf("vignette=angle=0.2"), f(ClipFilter(vignette = 1f)))
        val weak = f(ClipFilter(vignette = 0.1f)).single().substringAfter("angle=").toDouble()
        val strong = f(ClipFilter(vignette = 0.9f)).single().substringAfter("angle=").toDouble()
        assertTrue(weak > strong)
    }
    @Test fun grain_is_temporal_noise() {
        assertEquals(listOf("noise=alls=40:allf=t+u"), f(ClipFilter(grain = 1f)))
    }
    @Test fun the_order_is_grade_colour_curves_tone_detail_then_grain() {
        val all = f(ClipFilter(brightness = .1f, temperature = .1f, hue = .1f, shadows = .1f, tone = ToneMode.SEPIA, invert = true, sharpen = .1f, blur = .1f, vignette = .1f, grain = .1f))
        val names = all.map { it.substringBefore('=').substringBefore(':') }
        assertEquals(listOf("lutyuv", "colorbalance", "hue", "curves", "colorchannelmixer", "negate", "unsharp", "gblur", "vignette", "noise"), names)
    }
    @Test fun every_preset_builds_without_nan_or_scientific_notation() {
        for (p in FilterPresets.all) for (s in f(p.filter)) {
            assertTrue("${p.id}: $s", !s.contains("NaN") && !s.contains("E-") && !s.contains("Infinity"))
        }
    }
}
