package io.bsoft.bconverter.domain.render

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
    fun build(plan: RenderPlan): FilterGraph {
        val chains = mutableListOf<FilterChain>()
        chains += videoChains(plan)
        val audioOutput = audioChains(plan, chains)
        return FilterGraph(chains, videoOutput = "vout", audioOutput = audioOutput)
    }

    private fun videoChains(plan: RenderPlan): List<FilterChain> {
        val c = plan.canvas
        val chains = plan.videoSegments.mapIndexed { i, segment ->
            val retime = if (segment.speed == 1f) "setpts=PTS-STARTPTS" else "setpts=(PTS-STARTPTS)/${RenderNumbers.format(segment.speed.toDouble())}"
            FilterChain(
                listOf("${segment.inputIndex}:v"),
                listOf(
                    "scale=${c.width}:${c.height}:force_original_aspect_ratio=decrease",
                    "pad=${c.width}:${c.height}:(ow-iw)/2:(oh-ih)/2:color=black",
                    "setsar=1",
                    retime,
                    "fps=${RenderNumbers.format(c.frameRate, 3)}",
                    "format=yuv420p",
                ),
                listOf("v$i"),
            )
        }
        val labels = plan.videoSegments.indices.map { "v$it" }
        val join = if (labels.size == 1) "null" else "concat=n=${labels.size}:v=1:a=0"
        return chains + FilterChain(labels, listOf(join), listOf("vout"))
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
                chains += if (seg.inputIndex != null) {
                    FilterChain(
                        listOf("${seg.inputIndex}:a"),
                        listOf(base, "asetpts=PTS-STARTPTS") + AudioTempo.filters(seg.speed) + listOf("apad=whole_dur=$seconds", "atrim=duration=$seconds"),
                        listOf("a$j"),
                    )
                } else {
                    FilterChain(emptyList(), listOf("anullsrc=r=${format.sampleRateHz}:cl=$layout", "atrim=duration=$seconds"), listOf("a$j"))
                }
            }
            val labels = segments.indices.map { "a$it" }
            chains += FilterChain(labels, listOf(if (labels.size == 1) "anull" else "concat=n=${labels.size}:v=0:a=1"), listOf("aorig"))
            mixInputs += "aorig"
        }

        plan.overlays.forEachIndexed { k, overlay ->
            val filters = mutableListOf(base, "asetpts=PTS-STARTPTS")
            if (overlay.volume != 1f) filters += "volume=${RenderNumbers.format(overlay.volume.toDouble())}"
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
