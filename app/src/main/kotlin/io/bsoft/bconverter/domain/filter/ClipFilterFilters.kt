package io.bsoft.bconverter.domain.filter

import io.bsoft.bconverter.domain.render.RenderNumbers
import kotlin.math.PI
import kotlin.math.pow

/** Turns a [ClipFilter] into FFmpeg video filters (every one exists in FFmpeg 6 and in the FFmpegKit 8 build). */
object ClipFilterFilters {
    /** Every FFmpeg filter this file may emit; the device test checks each is in the device's own filter list. */
    val USED_FILTERS = listOf("lutyuv", "colorbalance", "hue", "curves", "colorchannelmixer", "negate", "unsharp", "gblur", "vignette", "noise")
    private const val BRIGHTNESS_RANGE = 0.3
    private const val CONTRAST_RANGE = 0.8
    private const val COLOR_SHIFT = 0.25
    private const val SHARPEN_AMOUNT = 1.5
    private const val BLUR_FRACTION_OF_HEIGHT = 0.012
    private const val GRAIN_STRENGTH = 40.0
    private const val VIGNETTE_WIDEST = PI / 2
    private const val VIGNETTE_TIGHTEST = 0.2
    private const val SEPIA = "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131:0"

    /** [frameHeight] is the output height: blur is a fraction of it so the look does not change with the resolution. */
    fun build(f: ClipFilter, frameHeight: Int): List<String> {
        val out = mutableListOf<String>()
        fun n(v: Double) = RenderNumbers.format(v)

        // Brightness, contrast and exposure are one table on the luma plane. (The device's FFmpegKit build has no `eq`
        // filter, and asking for a missing filter crashes the process, so only filters in USED_FILTERS may appear here.)
        if (f.brightness != 0f || f.contrast != 0f || f.exposure != 0f) {
            var e = if (f.exposure != 0f) "pow(val/maxval,${n(2.0.pow(-f.exposure.toDouble()))})" else "val/maxval"
            if (f.contrast != 0f) e = "($e-0.5)*${n(1 + f.contrast * CONTRAST_RANGE)}+0.5"
            if (f.brightness != 0f) { val b = n(f.brightness * BRIGHTNESS_RANGE); e += if (f.brightness > 0f) "+$b" else b }
            out += "lutyuv=y='clip($e,0,1)*maxval'"
        }

        if (f.temperature != 0f || f.tint != 0f) {
            val r = n(f.temperature * COLOR_SHIFT); val g = n(-f.tint * COLOR_SHIFT); val b = n(-f.temperature * COLOR_SHIFT)
            out += "colorbalance=rs=$r:gs=$g:bs=$b:rm=$r:gm=$g:bm=$b:rh=$r:gh=$g:bh=$b"
        }
        if (f.hue != 0f || f.saturation != 0f) {
            val parts = mutableListOf<String>()
            if (f.hue != 0f) parts += "h=${n(f.hue * 180.0)}"
            if (f.saturation != 0f) parts += "s=${n(1.0 + f.saturation)}"
            out += "hue=" + parts.joinToString(":")
        }

        curves(f)?.let { out += it }

        when (f.tone) {
            ToneMode.BLACK_WHITE -> out += "hue=s=0"
            ToneMode.SEPIA -> out += SEPIA
            ToneMode.NONE -> Unit
        }
        if (f.invert) out += "negate"

        if (f.sharpen > 0f) out += "unsharp=5:5:${n(f.sharpen * SHARPEN_AMOUNT)}:5:5:0"
        if (f.blur > 0f) out += "gblur=sigma=${n(f.blur * BLUR_FRACTION_OF_HEIGHT * frameHeight)}"
        if (f.vignette > 0f) out += "vignette=angle=${n(VIGNETTE_WIDEST - f.vignette * (VIGNETTE_WIDEST - VIGNETTE_TIGHTEST))}"
        if (f.grain > 0f) out += "noise=alls=${n(f.grain * GRAIN_STRENGTH)}:allf=t+u"
        return out
    }

    /** Fade lifts the black point and squeezes the range; shadows/highlights move the quarter points; curve bends them like an S. */
    private fun curves(f: ClipFilter): String? {
        if (f.shadows == 0f && f.highlights == 0f && f.curve == 0f && f.fade == 0f) return null
        val lift = f.fade * 0.25
        fun squeeze(x: Double) = lift + (1 - lift) * x
        fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
        val low = clamp(squeeze(0.25) + 0.15 * f.shadows - 0.1 * f.curve)
        val high = clamp(squeeze(0.75) + 0.15 * f.highlights + 0.1 * f.curve)
        fun n(v: Double) = RenderNumbers.format(v)
        return "curves=all='0/${n(lift)} 0.25/${n(low)} 0.75/${n(high)} 1/1'"
    }
}
