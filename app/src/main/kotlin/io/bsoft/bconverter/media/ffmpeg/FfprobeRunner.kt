package io.bsoft.bconverter.media.ffmpeg

import java.io.File

sealed interface FfprobeRunResult {
    /** Raw `ffprobe -print_format json -show_format -show_streams` output. */
    data class Success(val json: String) : FfprobeRunResult
    data class Failure(val exitCode: Int?) : FfprobeRunResult
}

/** Seam over the native FFprobe. The real implementation depends on the chosen FFmpeg distribution. */
interface FfprobeRunner {
    suspend fun run(file: File): FfprobeRunResult
}
