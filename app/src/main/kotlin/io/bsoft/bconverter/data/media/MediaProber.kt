package io.bsoft.bconverter.data.media

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.ffmpeg.FfprobeJsonParser
import io.bsoft.bconverter.media.ffmpeg.FfprobeParseResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunner
import kotlin.coroutines.cancellation.CancellationException

/** Resolve input → run ffprobe → parse. Releases staged input on every failure path. */
class MediaProber(
    private val resolver: InputResolver,
    private val runner: FfprobeRunner,
    private val parser: FfprobeJsonParser = FfprobeJsonParser(),
) {
    suspend fun probe(source: MediaSource): ProbeOutcome {
        val input = when (val resolved = resolver.resolve(source)) {
            is ResolveResult.Success -> resolved.input
            is ResolveResult.Failure -> return ProbeOutcome.Failure(ProbeFailure.Input(resolved.error))
        }
        val run = try {
            runner.run(input.file)
        } catch (e: CancellationException) {
            resolver.release(input); throw e
        }
        val json = when (run) {
            is FfprobeRunResult.Success -> run.json
            is FfprobeRunResult.Failure -> {
                resolver.release(input); return ProbeOutcome.Failure(ProbeFailure.ToolFailed(run.exitCode))
            }
        }
        return when (val parsed = parser.parse(json)) {
            is FfprobeParseResult.Parsed -> ProbeOutcome.Success(parsed.metadata, input)
            is FfprobeParseResult.Rejected -> { resolver.release(input); ProbeOutcome.Failure(parsed.failure) }
        }
    }
}
