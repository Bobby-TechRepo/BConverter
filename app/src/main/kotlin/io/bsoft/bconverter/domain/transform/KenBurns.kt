package io.bsoft.bconverter.domain.transform

import io.bsoft.bconverter.domain.render.RenderNumbers

/** A slow zoom or pan over one clip (the "Ken Burns" effect). The view moves in the direction named. */
enum class KenBurnsMode { NONE, ZOOM_IN, ZOOM_OUT, PAN_LEFT, PAN_RIGHT, PAN_UP, PAN_DOWN }

/** The part of the (transformed) picture that is shown: [zoom] 1 = all of it; [cx]/[cy] = the centre of the view as fractions of the picture. */
data class KenBurnsWindow(val zoom: Float, val cx: Float, val cy: Float)

/**
 * A motion from the first frame of the clip to its last. [amount] is the largest zoom reached (1.1–2): zoom modes go between 1 and
 * [amount]; pan modes keep [amount] zoom and slide the view from one edge of the picture to the other.
 */
data class KenBurns(val mode: KenBurnsMode = KenBurnsMode.NONE, val amount: Float = DEFAULT_AMOUNT) {
    init { require(amount.isFinite() && amount in MIN_AMOUNT..MAX_AMOUNT) { "amount must be within $MIN_AMOUNT..$MAX_AMOUNT" } }

    val isNone: Boolean get() = mode == KenBurnsMode.NONE

    private val lo: Float get() = 0.5f / amount
    private val hi: Float get() = 1f - lo

    fun start(): KenBurnsWindow = when (mode) {
        KenBurnsMode.NONE, KenBurnsMode.ZOOM_IN -> KenBurnsWindow(1f, 0.5f, 0.5f)
        KenBurnsMode.ZOOM_OUT -> KenBurnsWindow(amount, 0.5f, 0.5f)
        KenBurnsMode.PAN_LEFT -> KenBurnsWindow(amount, hi, 0.5f)
        KenBurnsMode.PAN_RIGHT -> KenBurnsWindow(amount, lo, 0.5f)
        KenBurnsMode.PAN_UP -> KenBurnsWindow(amount, 0.5f, hi)
        KenBurnsMode.PAN_DOWN -> KenBurnsWindow(amount, 0.5f, lo)
    }

    fun end(): KenBurnsWindow = when (mode) {
        KenBurnsMode.NONE, KenBurnsMode.ZOOM_OUT -> KenBurnsWindow(1f, 0.5f, 0.5f)
        KenBurnsMode.ZOOM_IN -> KenBurnsWindow(amount, 0.5f, 0.5f)
        KenBurnsMode.PAN_LEFT -> KenBurnsWindow(amount, lo, 0.5f)
        KenBurnsMode.PAN_RIGHT -> KenBurnsWindow(amount, hi, 0.5f)
        KenBurnsMode.PAN_UP -> KenBurnsWindow(amount, 0.5f, lo)
        KenBurnsMode.PAN_DOWN -> KenBurnsWindow(amount, 0.5f, hi)
    }

    companion object {
        const val MIN_AMOUNT = 1.1f
        const val MAX_AMOUNT = 2f
        const val DEFAULT_AMOUNT = 1.3f
        val None = KenBurns()
    }
}

/**
 * Turns a [KenBurns] into FFmpeg filters with `zoompan`, which keeps every frame the same size (cutting a window out of a picture
 * whose size changes per frame made some FFmpeg builds stall). The picture is first made frame-steady and, when small enough,
 * scaled up 2× so the window can move in half-pixel steps.
 */
object KenBurnsFilters {
    val USED_FILTERS = listOf("setpts", "fps", "scale", "zoompan")
    private const val SUPERSAMPLE_LIMIT = 3840

    /**
     * For a picture of [width] × [height] that lasts [sourceMs] in the input (before any speed change), made at [fps] frames a second.
     * A [reversed] clip is flipped afterwards, so its motion is written backwards to come out in the direction the user chose.
     */
    fun build(kb: KenBurns, width: Int, height: Int, sourceMs: Long, reversed: Boolean, fps: Double): List<String> {
        if (kb.isNone || width < 2 || height < 2 || !fps.isFinite() || fps <= 0.0) return emptyList()
        val w = width / 2 * 2
        val h = height / 2 * 2
        val a = if (reversed) kb.end() else kb.start()
        val b = if (reversed) kb.start() else kb.end()
        val frames = maxOf(1.0, maxOf(sourceMs, 40L) / 1000.0 * fps)
        val p = "clip(on/${RenderNumbers.format(frames, 2)},0,1)"
        fun n(v: Float) = RenderNumbers.format(v.toDouble(), 4)
        fun mix(from: Float, to: Float): String =
            if (from == to) n(from) else "(${n(from)}${if (to > from) "+" else "-"}${n(kotlin.math.abs(to - from))}*$p)"
        val z = mix(a.zoom, b.zoom)
        val x = "clip(${mix(a.cx, b.cx)}*iw-iw/$z/2,0,iw-iw/$z)"
        val y = "clip(${mix(a.cy, b.cy)}*ih-ih/$z/2,0,ih-ih/$z)"
        val rate = RenderNumbers.format(fps, 3)
        val up = if (w * 2 <= SUPERSAMPLE_LIMIT) listOf("scale=${w * 2}:${h * 2}:flags=bilinear") else emptyList()
        return listOf("setpts=PTS-STARTPTS", "fps=$rate") + up + "zoompan=z='$z':x='$x':y='$y':d=1:s=${w}x$h:fps=$rate"
    }
}

/**
 * Whether the editor shows the zoom/pan controls. The export did not finish on a real device (FFmpegKit 8.1) in zips 134 and 135, so
 * zip 136 hid the controls; the per-mode `KenBurnsRenderIntegrationTest` tests passed on the device afterwards (user, 2026-11-10), so
 * zip 141 shows them. Set to false again to hide them.
 */
object KenBurnsAvailability {
    const val EXPORT_VERIFIED = true
}
