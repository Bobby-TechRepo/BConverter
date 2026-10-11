package io.bsoft.bconverter.media.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Real FFmpeg via FFmpegKit: async session, statistics callback for progress, cancel by session id. */
class FfmpegKitExecutor : FfmpegExecutor {
    private val sessions = ConcurrentHashMap<String, Long>()

    override suspend fun execute(
        jobId: String,
        args: List<String>,
        onStatistics: (Long, Double?) -> Unit,
    ): FfmpegExecResult = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            FFmpegSessionCompleteCallback { finished ->
                sessions.remove(jobId)
                val code = finished.returnCode
                continuation.resume(
                    when {
                        ReturnCode.isSuccess(code) -> FfmpegExecResult.Success
                        ReturnCode.isCancel(code) -> FfmpegExecResult.Cancelled
                        else -> FfmpegExecResult.Failure(code?.value)
                    },
                )
            },
            null,
            StatisticsCallback { stats -> onStatistics(stats.time.toLong(), stats.speed.toDouble().takeIf { it > 0 }) },
        )
        sessions[jobId] = session.sessionId
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    override suspend fun cancel(jobId: String) {
        sessions[jobId]?.let { FFmpegKit.cancel(it) }
    }
}
