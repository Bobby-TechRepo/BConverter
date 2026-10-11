package io.bsoft.bconverter.media.ffmpeg

import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Real FFprobe via FFmpegKit. Argument-array form, so paths with spaces need no quoting. */
class FfmpegKitFfprobeRunner(private val ioDispatcher: CoroutineDispatcher) : FfprobeRunner {
    override suspend fun run(file: File): FfprobeRunResult = withContext(ioDispatcher) {
        val session = FFprobeKit.executeWithArguments(
            arrayOf("-v", "error", "-hide_banner", "-print_format", "json", "-show_format", "-show_streams", file.absolutePath),
        )
        if (ReturnCode.isSuccess(session.returnCode)) FfprobeRunResult.Success(session.output.orEmpty())
        else FfprobeRunResult.Failure(session.returnCode?.value)
    }
}
