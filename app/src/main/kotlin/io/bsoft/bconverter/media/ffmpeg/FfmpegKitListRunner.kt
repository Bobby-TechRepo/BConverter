package io.bsoft.bconverter.media.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class FfmpegKitListRunner(private val ioDispatcher: CoroutineDispatcher) : FfmpegListRunner {
    override suspend fun listEncoders(): String? = run("-encoders")
    override suspend fun listDecoders(): String? = run("-decoders")

    private suspend fun run(flag: String): String? = withContext(ioDispatcher) {
        val session = FFmpegKit.executeWithArguments(arrayOf("-hide_banner", flag))
        if (ReturnCode.isSuccess(session.returnCode)) session.output else null
    }
}
