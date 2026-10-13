package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoStream

/**
 * Rough output-size estimates (spec §36). Heuristics, intentionally on the high side:
 * bitrate × duration plus 5 % container overhead. CRF output is assumed to be at most the source video bitrate
 * (scaled by pixel count when downscaling). Falls back to the input size when the duration is unknown.
 */
object StorageEstimator {
    private const val OVERHEAD = 1.05
    private const val DEFAULT_AUDIO_BPS = 128_000.0
    private const val LOSSLESS_AUDIO_BPS = 1_411_000.0 // CD-quality PCM
    private val LOSSLESS = setOf(AudioCodec.FLAC, AudioCodec.PCM)

    fun estimateConversion(settings: ConversionSettings, metadata: MediaMetadata, inputSizeBytes: Long): Long {
        val seconds = metadata.durationMs / 1000.0
        if (seconds <= 0) return inputSizeBytes
        val videoStream = metadata.videoStreams.firstOrNull()
        val sourceAudioBps = metadata.audioStreams.firstOrNull()?.bitrateBps?.toDouble() ?: DEFAULT_AUDIO_BPS
        val audioShare = if (metadata.hasAudio) sourceAudioBps else 0.0
        val sourceVideoBps = videoStream?.bitrateBps?.toDouble()
            ?: (inputSizeBytes * 8.0 / seconds - audioShare).coerceAtLeast(0.0)

        val videoBps = when (val plan = settings.video) {
            VideoPlan.Copy -> sourceVideoBps
            is VideoPlan.Encode -> when (val quality = plan.settings.quality) {
                is VideoQuality.Bitrate -> quality.kbps * 1000.0
                is VideoQuality.Crf -> sourceVideoBps * pixelRatio(plan.settings.resolution, videoStream)
            }
        }
        val audioBps = when (val plan = settings.audio) {
            AudioPlan.None -> 0.0
            AudioPlan.Copy -> audioShare
            is AudioPlan.Encode -> encodedAudioBps(plan.settings.codec, plan.settings.bitrateKbps)
        }
        return ((videoBps + audioBps) / 8.0 * seconds * OVERHEAD).toLong()
    }

    fun estimateExtraction(settings: AudioExtractionSettings, metadata: MediaMetadata, inputSizeBytes: Long): Long {
        val seconds = metadata.durationMs / 1000.0
        if (seconds <= 0) return inputSizeBytes
        val bps = when (val mode = settings.mode) {
            AudioExtractionMode.Copy -> metadata.audioStreams.getOrNull(settings.streamOrdinal)?.bitrateBps?.toDouble() ?: DEFAULT_AUDIO_BPS
            is AudioExtractionMode.Encode -> encodedAudioBps(settings.format.codec, mode.bitrateKbps)
        }
        return (bps / 8.0 * seconds * OVERHEAD).toLong()
    }

    private fun encodedAudioBps(codec: AudioCodec, kbps: Int?): Double =
        kbps?.times(1000.0) ?: if (codec in LOSSLESS) LOSSLESS_AUDIO_BPS else DEFAULT_AUDIO_BPS

    private fun pixelRatio(resolution: Resolution?, stream: VideoStream?): Double {
        if (resolution == null || stream == null) return 1.0
        val source = stream.displayWidth.toDouble() * stream.displayHeight
        if (source <= 0) return 1.0
        return minOf(1.0, resolution.width.toDouble() * resolution.height / source)
    }
}
