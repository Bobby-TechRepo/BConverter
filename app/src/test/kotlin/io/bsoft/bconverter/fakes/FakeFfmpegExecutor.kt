package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.media.ffmpeg.FfmpegExecResult
import io.bsoft.bconverter.media.ffmpeg.FfmpegExecutor

class FakeFfmpegExecutor : FfmpegExecutor {
    data class Call(val jobId: String, val args: List<String>)

    var result: FfmpegExecResult = FfmpegExecResult.Success
    var statistics: List<Pair<Long, Double?>> = emptyList()
    val calls = mutableListOf<Call>()
    val cancelled = mutableListOf<String>()

    override suspend fun execute(jobId: String, args: List<String>, onStatistics: (Long, Double?) -> Unit): FfmpegExecResult {
        calls += Call(jobId, args)
        statistics.forEach { (ms, speed) -> onStatistics(ms, speed) }
        return result
    }

    override suspend fun cancel(jobId: String) { cancelled += jobId }
}
