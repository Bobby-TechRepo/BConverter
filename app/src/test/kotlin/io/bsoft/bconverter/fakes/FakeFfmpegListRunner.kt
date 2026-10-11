package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.media.ffmpeg.FfmpegListRunner

class FakeFfmpegListRunner(var encoders: String? = null, var decoders: String? = null) : FfmpegListRunner {
    var encoderCalls = 0; private set
    var decoderCalls = 0; private set
    override suspend fun listEncoders(): String? { encoderCalls++; return encoders }
    override suspend fun listDecoders(): String? { decoderCalls++; return decoders }
}
