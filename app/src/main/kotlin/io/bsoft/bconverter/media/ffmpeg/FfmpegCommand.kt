package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.media.api.ConversionRequest

/** Ordered option list, so tests can assert on structure instead of fragile strings. */
data class FfmpegOptions(val entries: List<Pair<String, String?>> = emptyList()) {
    fun value(name: String): String? = entries.firstOrNull { it.first == name }?.second
    fun values(name: String): List<String> = entries.filter { it.first == name }.mapNotNull { it.second }
    fun has(name: String): Boolean = entries.any { it.first == name }
    fun toArguments(): List<String> = entries.flatMap { listOfNotNull(it.first, it.second) }
}

data class FfmpegInput(val path: String, val options: FfmpegOptions = FfmpegOptions())
data class FfmpegOutput(val path: String, val options: FfmpegOptions)

data class FfmpegCommand(
    val globalOptions: List<String>,
    val inputs: List<FfmpegInput>,
    val output: FfmpegOutput,
) {
    /** One element per argument — no shell quoting needed, so paths with spaces stay intact. */
    fun toArguments(): List<String> =
        globalOptions + inputs.flatMap { it.options.toArguments() + listOf("-i", it.path) } +
            output.options.toArguments() + output.path
}

/** Spec §30. `buildRenderCommand(project)` is added in Phase 12 together with the render pipeline. */
interface FfmpegCommandBuilder {
    fun buildConversionCommand(request: ConversionRequest): FfmpegCommand
}
