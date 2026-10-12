package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.data.media.DefaultMediaCapabilityRepository
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository
import io.bsoft.bconverter.media.device.MediaCodecCapabilitySource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Discovers what the installed FFmpeg build can really do (once, then cached). The device's MediaCodec list is
 * only used to confirm that FFmpeg's `*_mediacodec` wrappers have hardware behind them.
 * If FFmpeg cannot be queried, nothing is offered and nothing is cached, so the next call retries.
 */
class CapabilityProvider(
    private val listRunner: FfmpegListRunner,
    private val device: MediaCodecCapabilitySource,
) : CapabilitySource {
    private class Loaded(val repository: MediaCapabilityRepository, val names: EncoderNames)

    private val mutex = Mutex()
    private var cached: Loaded? = null

    override suspend fun repository(): MediaCapabilityRepository = load().repository
    suspend fun encoderNames(): EncoderNames = load().names

    private suspend fun load(): Loaded = mutex.withLock {
        cached ?: run {
            val encoders = listRunner.listEncoders()
            val decoders = listRunner.listDecoders()
            if (encoders == null || decoders == null) {
                Loaded(DefaultMediaCapabilityRepository(emptyList()), EncoderNames.Default)
            } else {
                val deviceEncoders = device.availability().filter { it.canEncode }.map { it.codec }.toSet()
                Loaded(
                    DefaultMediaCapabilityRepository(FfmpegCapabilities.fromListings(encoders, decoders, deviceEncoders)),
                    FfmpegCapabilities.encoderNames(encoders, deviceEncoders),
                ).also { cached = it }
            }
        }
    }
}
