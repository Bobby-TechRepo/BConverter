package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunner
import java.io.File

class FakeFfprobeRunner : FfprobeRunner {
    var result: FfprobeRunResult = FfprobeRunResult.Success("{}")
    var throwOnRun: Throwable? = null
    val files = mutableListOf<File>()

    override suspend fun run(file: File): FfprobeRunResult {
        files += file
        throwOnRun?.let { throw it }
        return result
    }
}
