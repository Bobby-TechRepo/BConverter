package io.bsoft.bconverter.media.ffmpeg

sealed interface FfmpegExecResult {
    data object Success : FfmpegExecResult
    /** [logTail]: the end of FFmpeg's log for this run (why it failed), when the executor captured it. */
    data class Failure(val exitCode: Int?, val logTail: String? = null) : FfmpegExecResult
    data object Cancelled : FfmpegExecResult
}

/** Seam over the native FFmpeg: runs an argument list, reports progress, can be cancelled by job id. */
interface FfmpegExecutor {
    suspend fun execute(jobId: String, args: List<String>, onStatistics: (processedMs: Long, speed: Double?) -> Unit): FfmpegExecResult
    suspend fun cancel(jobId: String)
}

/** Seam used to discover which encoders/decoders the installed build contains. */
interface FfmpegListRunner {
    suspend fun listEncoders(): String?
    suspend fun listDecoders(): String?
}
