package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.timeline.PipShape
import io.bsoft.bconverter.domain.filter.ClipFilterFilters
import io.bsoft.bconverter.domain.transform.ClipTransformFilters
import io.bsoft.bconverter.domain.transform.KenBurnsFilters
import io.bsoft.bconverter.domain.timeline.TransitionType
import java.math.BigDecimal
import java.math.RoundingMode

/** `[in1][in2]filter,filter[out]`; an empty input list is a source chain (e.g. generated silence). */
data class FilterChain(val inputLabels: List<String>, val filters: List<String>, val outputLabels: List<String>) {
    fun render(): String =
        inputLabels.joinToString("") { "[$it]" } + filters.joinToString(",") + outputLabels.joinToString("") { "[$it]" }
}

data class FilterGraph(val chains: List<FilterChain>, val videoOutput: String, val audioOutput: String?) {
    /** The value for FFmpeg's `-filter_complex`. */
    fun render(): String = chains.joinToString(";") { it.render() }
}

object RenderNumbers {
    /** Plain decimal text without trailing zeros: 30.0 → "30", 29.97 → "29.97". */
    fun format(value: Double, scale: Int = 4): String =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun seconds(ms: Long): String = format(ms / 1000.0, 3)
}

/** `atempo` only accepts 0.5–2 in every FFmpeg version, so larger/smaller speeds are chained. */
object AudioTempo {
    private const val EPS = 1e-6

    fun filters(speed: Float): List<String> {
        var remaining = speed.toDouble()
        val factors = mutableListOf<Double>()
        while (remaining > 2.0 + EPS) { factors += 2.0; remaining /= 2.0 }
        while (remaining < 0.5 - EPS) { factors += 0.5; remaining /= 0.5 }
        if (kotlin.math.abs(remaining - 1.0) > EPS) factors += remaining
        return factors.map { "atempo=${RenderNumbers.format(it)}" }
    }
}

/** Builds the `-filter_complex` graph for a [RenderPlan]: per-clip video chains + concat, audio chains + mix. */
object FilterGraphBuilder {
    /** The blur of a bar picture, as a share of the (small) background's height. */
    private const val BLUR_SIGMA_OF_HEIGHT = 0.04
    private const val BLUR_FALLBACK_RGB = 0x000000
    fun build(plan: RenderPlan): FilterGraph {
        val chains = mutableListOf<FilterChain>()
        chains += videoChains(plan)
        val audioOutput = audioChains(plan, chains)
        return FilterGraph(chains, videoOutput = pipChains(plan, chains), audioOutput = audioOutput)
    }

    /** The fade at the start of segment [i] when the cut before it is a fade through black (half of it lies on each side of the cut). */
    private fun fadeIn(plan: RenderPlan, i: Int): Long? =
        plan.videoSegments[i].join.takeIf { it.type == TransitionType.FADE_BLACK && it.durationMs > 0 }?.let { it.durationMs / 2 }

    /** The fade at the end of segment [i] when the cut after it is a fade through black. */
    private fun fadeOut(plan: RenderPlan, i: Int): Long? = plan.videoSegments.getOrNull(i + 1)
        ?.join?.takeIf { it.type == TransitionType.FADE_BLACK && it.durationMs > 0 }?.let { it.durationMs / 2 }

    /** The scaled picture is smaller than the canvas on some side, so there is something to fill. Unknown sizes count as "yes". */
    private fun hasBars(segment: VideoSegment, c: Canvas): Boolean {
        if (segment.sourceWidth <= 0 || segment.sourceHeight <= 0) return true
        val (w, h) = segment.transform.outputSize(segment.sourceWidth, segment.sourceHeight)
        if (w <= 0 || h <= 0) return true
        val scale = minOf(c.width.toDouble() / w, c.height.toDouble() / h)
        return c.width - w * scale > 1.5 || c.height - h * scale > 1.5
    }

    /** One eighth of the canvas side, even, at least 2. */
    private fun backgroundSize(side: Int): Int = maxOf(2, (side / 8) / 2 * 2)

    private fun isCrossfade(segment: VideoSegment) = segment.join.type == TransitionType.CROSSFADE && segment.join.durationMs > 0

    /** Clips are joined in runs: neighbours with a plain cut or a fade through black are concatenated, a crossfade starts a new run. */
    private fun runs(plan: RenderPlan): List<List<Int>> {
        val result = mutableListOf<MutableList<Int>>()
        plan.videoSegments.forEachIndexed { i, segment ->
            if (i == 0 || isCrossfade(segment)) result += mutableListOf(i) else result.last() += i
        }
        return result
    }

    private fun videoChains(plan: RenderPlan): List<FilterChain> {
        val c = plan.canvas
        val anyCrossfade = plan.videoSegments.any(::isCrossfade)
        val chains = plan.videoSegments.flatMapIndexed { i, segment ->
            val retime = if (segment.speed == 1f) "setpts=PTS-STARTPTS" else "setpts=(PTS-STARTPTS)/${RenderNumbers.format(segment.speed.toDouble())}"
            val head = mutableListOf<String>()
            // A freeze frame keeps only the first picture of its input.
            if (segment.freeze) { head += "trim=end_frame=1"; head += "setpts=PTS-STARTPTS" }
            // Crop and turn first, on the picture as it was shot, then fit it to the canvas.
            head += ClipTransformFilters.build(segment.transform, segment.sourceWidth, segment.sourceHeight)
            // A slow zoom or pan on the turned picture, before it is fitted to the canvas.
            if (!segment.freeze && segment.sourceWidth > 0 && segment.sourceHeight > 0) {
                val (kw, kh) = segment.transform.outputSize(segment.sourceWidth, segment.sourceHeight)
                head += KenBurnsFilters.build(segment.kenBurns, kw, kh, segment.sourceMs, segment.reversed, plan.canvas.frameRate)
            }
            val fill = plan.fit == CanvasFit.FILL
            // Fill covers the whole canvas and crops what overflows; fit keeps the whole picture and leaves bars.
            head += if (fill) listOf("scale=${c.width}:${c.height}:force_original_aspect_ratio=increase", "crop=${c.width}:${c.height}")
            else listOf("scale=${c.width}:${c.height}:force_original_aspect_ratio=decrease")
            // `reverse` buffers the whole clip, so it runs on the already-scaled picture (ReversePolicy keeps that small).
            if (segment.reversed) head += "reverse"
            // The look is applied to the picture itself, before the bars, so a plain bar stays the bar colour.
            head += ClipFilterFilters.build(segment.filter, c.height)
            val blurred = !fill && plan.background == CanvasBackground.Blur && hasBars(segment, c)
            val tail = mutableListOf<String>()
            if (segment.freeze) {
                // One picture, repeated for the whole length (the first picture lasts one frame, so the repeat is cut to the exact time).
                val seconds = RenderNumbers.seconds(segment.durationMs)
                tail += listOf("setpts=PTS-STARTPTS", "fps=${RenderNumbers.format(c.frameRate, 3)}", "tpad=stop_mode=clone:stop_duration=$seconds", "trim=duration=$seconds", "setpts=PTS-STARTPTS", "format=yuv420p")
            } else {
                tail += listOf(retime, "fps=${RenderNumbers.format(c.frameRate, 3)}", "format=yuv420p")
            }
            fadeIn(plan, i)?.let { tail += "fade=t=in:st=0:d=${RenderNumbers.seconds(it)}" }
            fadeOut(plan, i)?.let { tail += "fade=t=out:st=${RenderNumbers.seconds(segment.durationMs - it)}:d=${RenderNumbers.seconds(it)}" }
            if (anyCrossfade) tail += "settb=AVTB" // xfade needs both inputs on the same time base
            if (blurred) {
                // The picture is laid over an enlarged, blurred copy of itself; the copy is made small first, which is fast and smooth.
                val bw = backgroundSize(c.width); val bh = backgroundSize(c.height)
                val radius = RenderNumbers.format(bh * BLUR_SIGMA_OF_HEIGHT, 2)
                listOf(
                    FilterChain(listOf("${segment.inputIndex}:v"), head + "split=2", listOf("f$i", "g$i")),
                    FilterChain(
                        listOf("g$i"),
                        listOf("scale=$bw:$bh:force_original_aspect_ratio=increase", "crop=$bw:$bh", "gblur=sigma=$radius", "scale=${c.width}:${c.height}:flags=bilinear"),
                        listOf("b$i"),
                    ),
                    FilterChain(listOf("b$i", "f$i"), listOf("overlay=(W-w)/2:(H-h)/2", "setsar=1") + tail, listOf("v$i")),
                )
            } else {
                val bar = (plan.background as? CanvasBackground.Color)?.rgb ?: BLUR_FALLBACK_RGB
                val pad = if (fill) emptyList() else listOf("pad=${c.width}:${c.height}:(ow-iw)/2:(oh-ih)/2:color=${if (bar == 0) "black" else "0x%06X".format(bar)}")
                listOf(FilterChain(listOf("${segment.inputIndex}:v"), head + pad + "setsar=1" + tail, listOf("v$i")))
            }
        }
        val runs = runs(plan)
        if (runs.size == 1) {
            val labels = runs[0].map { "v$it" }
            val join = if (labels.size == 1) "null" else "concat=n=${labels.size}:v=1:a=0"
            return chains + FilterChain(labels, listOf(join), listOf("vout"))
        }
        val joined = mutableListOf<FilterChain>()
        val runLabels = runs.mapIndexed { k, run ->
            if (run.size == 1) "v${run[0]}" else "r$k".also { joined += FilterChain(run.map { "v$it" }, listOf("concat=n=${run.size}:v=1:a=0", "settb=AVTB"), listOf(it)) }
        }
        var current = runLabels[0]
        for (k in 1 until runs.size) {
            val first = plan.videoSegments[runs[k][0]]
            val output = if (k == runs.lastIndex) "vout" else "x$k"
            joined += FilterChain(
                listOf(current, runLabels[k]),
                listOf("xfade=transition=fade:duration=${RenderNumbers.seconds(first.join.durationMs)}:offset=${RenderNumbers.seconds(first.startMs)}", "settb=AVTB"),
                listOf(output),
            )
            current = output
        }
        return chains + joined
    }

    /** The clip's own level and fades; the fade-out starts relative to the clip's end on the timeline. */
    private fun soundFilters(volume: Float, fadeInMs: Long, fadeOutMs: Long, durationMs: Long): List<String> {
        val out = mutableListOf<String>()
        if (volume != 1f) out += "volume=${RenderNumbers.format(volume.toDouble())}"
        val (fin, fout) = ClipSound.fit(fadeInMs, fadeOutMs, durationMs)
        if (fin > 0) out += "afade=t=in:st=0:d=${RenderNumbers.seconds(fin)}"
        if (fout > 0) out += "afade=t=out:st=${RenderNumbers.seconds(durationMs - fout)}:d=${RenderNumbers.seconds(fout)}"
        return out
    }

    /**
     * Lays each picture-in-picture clip over the finished video, first to last. A clip is put at its time on the timeline, scaled to its
     * width, given its opacity, and drawn only while it plays (`eof_action=pass` lets the video continue when it ends).
     * Returns the label of the video after all of them.
     */
    /**
     * A value that is straight lines between the keyframe values: the first value plus, for each step, its change times how far
     * (0..1) the time [timeVar] has got along that step. No conditionals, so it also works per frame inside `scale`/`geq`/`overlay`.
     */
    internal fun piecewise(startMs: Long, points: List<Pair<Long, Double>>, timeVar: String, digits: Int = 2, eased: List<Boolean> = emptyList()): String {
        val sb = StringBuilder(RenderNumbers.format(points.first().second, digits))
        points.zipWithNext().forEachIndexed { i, (a, b) ->
            val dv = b.second - a.second
            if (kotlin.math.abs(dv) < 1e-6 || b.first <= a.first) return@forEachIndexed
            val p = "clip((" + timeVar + "-" + RenderNumbers.seconds(startMs + a.first) + ")/" + RenderNumbers.seconds(b.first - a.first) + ",0,1)"
            // An eased step is the smoothstep of its progress.
            sb.append("+(").append(RenderNumbers.format(dv, digits)).append(")*").append(if (eased.getOrNull(i) == true) "($p*$p*(3-2*$p))" else p)
        }
        return sb.toString()
    }

    private fun pipChains(plan: RenderPlan, chains: MutableList<FilterChain>): String {
        var current = "vout"
        plan.pips.forEachIndexed { k, p ->
            val picture = "pip${k + 1}"
            val next = "pv${k + 1}"
            val start = RenderNumbers.seconds(p.startMs)
            val end = RenderNumbers.seconds(p.startMs + p.durationMs)
            // Along a glide the progress is 0 at the clip's start and 1 at its end (eased: smoothstep), per frame.
            val m = p.motion
            val progress = run {
                val raw = "clip((t-$start)/${RenderNumbers.seconds(p.durationMs)},0,1)"
                if (m?.eased == true) "($raw*$raw*(3-2*$raw))" else raw
            }
            val kf = p.keyframes
            val widthsVary = kf.size > 1 && kf.map { it.width }.distinct().size > 1
            // The width (px, as an expression of the time) of a clip that changes size, else null; the picture is made at the largest of them.
            val sizeExpr: String? = when {
                widthsVary -> piecewise(p.startMs, kf.map { it.offsetMs to it.width * plan.canvas.width.toDouble() }, "t", eased = kf.map { it.eased })
                p.endWidthPx != null -> "${p.widthPx}+(${p.endWidthPx - p.widthPx})*$progress"
                else -> null
            }
            val baseWidth = when {
                widthsVary -> kf.maxOf { it.width * plan.canvas.width }.toInt().let { it - it % 2 }.coerceAtLeast(2)
                p.endWidthPx != null -> maxOf(p.widthPx, p.endWidthPx)
                else -> p.widthPx
            }
            val look = buildList {
                add(if (p.speed == 1f) "setpts=PTS-STARTPTS+$start/TB" else "setpts=(PTS-STARTPTS)/${RenderNumbers.format(p.speed.toDouble())}+$start/TB")
                // A circle is cut from the middle of a square.
                if (p.shape == PipShape.CIRCLE) add("crop=min(iw\\,ih):min(iw\\,ih)")
                // The picture is cut, keyed, masked and faded at its largest size; a clip that changes size is shrunk per frame at the very end,
                // because filters such as `geq` keep the size they were first given and would freeze the animation.
                add("scale=$baseWidth:-2")
                // The key is taken out first, so the frame added after it stays solid.
                p.chromaKey?.let { add("chromakey=color=0x${"%06X".format(it.rgb)}:similarity=${RenderNumbers.format(it.similarity.toDouble(), 3)}:blend=${RenderNumbers.format(it.blend.toDouble(), 3)}") }
                if (p.borderPx > 0) add("pad=iw+${2 * p.borderPx}:ih+${2 * p.borderPx}:${p.borderPx}:${p.borderPx}:color=0x${"%06X".format(p.borderRgb)}")
                add("format=rgba")
                if (p.shape != PipShape.RECTANGLE) add(PipMask.filter(p.shape))
                // Fades change the picture's own transparency, so the video below shows through; they run on the clip's place on the timeline.
                if (p.fadeInMs > 0) add("fade=t=in:st=$start:d=${RenderNumbers.seconds(p.fadeInMs)}:alpha=1")
                if (p.fadeOutMs > 0) add("fade=t=out:st=${RenderNumbers.seconds(p.startMs + p.durationMs - p.fadeOutMs)}:d=${RenderNumbers.seconds(p.fadeOutMs)}:alpha=1")
                if (kf.map { it.opacity }.distinct().size > 1) add("geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='alpha(X,Y)*(${piecewise(p.startMs, kf.map { it.offsetMs to it.opacity.toDouble() }, "T", 3, kf.map { it.eased })})'")
                else if (p.opacity < 1f) add("colorchannelmixer=aa=${RenderNumbers.format(p.opacity.toDouble(), 2)}")
                if (sizeExpr != null) add("scale=w='2*trunc(($sizeExpr)/2)':h=-2:eval=frame")
            }
            chains += FilterChain(listOf("${p.inputIndex}:v"), look, listOf(picture))
            val x = RenderNumbers.format(p.centerX.toDouble() * plan.canvas.width, 2)
            val y = RenderNumbers.format(p.centerY.toDouble() * plan.canvas.height, 2)
            // A gliding clip's centre is the start plus the way to the end times the (eased) progress through the clip, per frame.
            fun centre(start: String, end: Double?, extent: Int): String =
                if (end == null) start else "$start+(${RenderNumbers.format(end * extent - start.toDouble(), 2)})*$progress"
            val cx = if (kf.size > 1) piecewise(p.startMs, kf.map { it.offsetMs to it.x * plan.canvas.width.toDouble() }, "t", eased = kf.map { it.eased }) else centre(x, m?.endX?.toDouble(), plan.canvas.width)
            val cy = if (kf.size > 1) piecewise(p.startMs, kf.map { it.offsetMs to it.y * plan.canvas.height.toDouble() }, "t", eased = kf.map { it.eased }) else centre(y, m?.endY?.toDouble(), plan.canvas.height)
            chains += FilterChain(
                listOf(current, picture),
                listOf("overlay=x='$cx-w/2':y='$cy-h/2':enable='between(t,$start,$end)':eof_action=pass:format=auto"),
                listOf(next),
            )
            current = next
        }
        return current
    }

    /** Adds the audio chains and returns the final audio label, or null when the output has no audio. */
    private fun audioChains(plan: RenderPlan, chains: MutableList<FilterChain>): String? {
        val format = plan.audioFormat ?: return null
        val layout = if (format.channels == 1) "mono" else "stereo"
        val base = "aformat=sample_rates=${format.sampleRateHz}:channel_layouts=$layout"
        val mixInputs = mutableListOf<String>()

        plan.originalAudio?.let { segments ->
            segments.forEachIndexed { j, seg ->
                val seconds = RenderNumbers.seconds(seg.timelineDurationMs)
                // A fade through black fades the sound too, on the same two sides of the cut.
                val fades = mutableListOf<String>()
                fadeIn(plan, j)?.let { fades += "afade=t=in:st=0:d=${RenderNumbers.seconds(it)}" }
                fadeOut(plan, j)?.let { fades += "afade=t=out:st=${RenderNumbers.seconds(seg.timelineDurationMs - it)}:d=${RenderNumbers.seconds(it)}" }
                chains += if (seg.inputIndex != null) {
                    FilterChain(
                        listOf("${seg.inputIndex}:a"),
                        listOf(base, "asetpts=PTS-STARTPTS") + (if (seg.reversed) listOf("areverse") else emptyList()) + AudioTempo.filters(seg.speed) + listOf("apad=whole_dur=$seconds", "atrim=duration=$seconds") +
                            soundFilters(seg.volume, seg.fadeInMs, seg.fadeOutMs, seg.timelineDurationMs) + fades,
                        listOf("a$j"),
                    )
                } else {
                    FilterChain(emptyList(), listOf("anullsrc=r=${format.sampleRateHz}:cl=$layout", "atrim=duration=$seconds") + fades, listOf("a$j"))
                }
            }
            val runs = runs(plan)
            if (runs.size == 1) {
                val labels = segments.indices.map { "a$it" }
                chains += FilterChain(labels, listOf(if (labels.size == 1) "anull" else "concat=n=${labels.size}:v=0:a=1"), listOf("aorig"))
            } else {
                val runLabels = runs.mapIndexed { k, run ->
                    if (run.size == 1) "a${run[0]}" else "ar$k".also { chains += FilterChain(run.map { "a$it" }, listOf("concat=n=${run.size}:v=0:a=1"), listOf(it)) }
                }
                var current = runLabels[0]
                for (k in 1 until runs.size) {
                    val output = if (k == runs.lastIndex) "aorig" else "ax$k"
                    val ms = plan.videoSegments[runs[k][0]].join.durationMs
                    chains += FilterChain(listOf(current, runLabels[k]), listOf("acrossfade=d=${RenderNumbers.seconds(ms)}"), listOf(output))
                    current = output
                }
            }
            mixInputs += "aorig"
        }

        plan.overlays.forEachIndexed { k, overlay ->
            val filters = mutableListOf(base, "asetpts=PTS-STARTPTS")
            filters += AudioTempo.filters(overlay.speed)
            filters += soundFilters(overlay.volume, overlay.fadeInMs, overlay.fadeOutMs, overlay.durationMs)
            if (overlay.delayMs > 0) filters += "adelay=" + if (format.channels == 1) "${overlay.delayMs}" else "${overlay.delayMs}|${overlay.delayMs}"
            chains += FilterChain(listOf("${overlay.inputIndex}:a"), filters, listOf("o$k"))
            mixInputs += "o$k"
        }

        if (mixInputs.isEmpty()) return null
        // normalize=0 keeps each clip at the volume the user set (no automatic 1/N scaling).
        val mix = if (mixInputs.size == 1) "anull" else "amix=inputs=${mixInputs.size}:duration=longest:normalize=0"
        chains += FilterChain(mixInputs, listOf(mix), listOf("aout"))
        return "aout"
    }
}

/** The `geq` filter that makes the corners (or the whole outside of a circle) of a picture transparent, with a soft one-pixel edge. */
object PipMask {
    fun filter(shape: PipShape): String {
        val r = if (shape == PipShape.CIRCLE) "(min(W,H)/2)" else "(${PipShape.ROUNDED_RADIUS}*min(W,H))"
        val alpha = "alpha(X,Y)*clip($r-hypot(max(abs(X+0.5-W/2)-(W/2-$r),0),max(abs(Y+0.5-H/2)-(H/2-$r),0))+0.5,0,1)"
        return "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='$alpha'"
    }
}
