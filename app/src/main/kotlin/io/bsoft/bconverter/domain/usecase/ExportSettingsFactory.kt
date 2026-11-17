package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.ExportSize
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.model.crfRange
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository
import io.bsoft.bconverter.domain.timeline.ExportSettings
import kotlin.math.roundToInt

/**
 * Chooses export settings for a preset using only encoders the build really has. Encoders without CRF support get a
 * bitrate of pixels × 30 fps × bits-per-pixel. [reference] is the first clip's display size (null if unknown).
 * Returns null when no video encoder exists for the container; the audio is null (silent) when no audio encoder does.
 */
class ExportSettingsFactory(private val capabilities: MediaCapabilityRepository) {

    fun create(preset: ExportPreset, container: ContainerFormat, reference: Resolution?, size: ExportSize = ExportSize.AUTO): ExportSettings? {
        val availableVideo = capabilities.getVideoCodecs(container)
        val videoCodec = CodecPreferences.VIDEO.firstOrNull { it in availableVideo } ?: return null
        val canvas = when (size) {
            ExportSize.AUTO -> if (preset == ExportPreset.SMALL_FILE) cappedTo720(reference) else null
            else -> size.cap(reference)
        }
        val effective = canvas ?: reference ?: FULL_HD
        val quality = if (videoCodec.crfRange() != null && capabilities.supportsCrf(videoCodec)) {
            VideoQuality.Crf(crf(preset))
        } else {
            VideoQuality.Bitrate(bitrateKbps(effective, preset))
        }
        val availableAudio = capabilities.getAudioCodecs(container)
        val audio = CodecPreferences.AUDIO.firstOrNull { it in availableAudio }?.let {
            AudioSettings(it, if (it in LOSSLESS) null else audioKbps(preset))
        }
        return ExportSettings(container, VideoSettings(videoCodec, quality, resolution = canvas), audio, keepOriginalAudio = true)
    }

    private fun cappedTo720(reference: Resolution?): Resolution? {
        if (reference == null || reference.height <= SMALL_MAX_HEIGHT) return null
        val width = reference.width.toDouble() * SMALL_MAX_HEIGHT / reference.height
        return Resolution(((width / 2).roundToInt() * 2).coerceAtLeast(2), SMALL_MAX_HEIGHT)
    }

    private fun crf(preset: ExportPreset) = when (preset) {
        ExportPreset.SMALL_FILE -> 28
        ExportPreset.BALANCED -> 23
        ExportPreset.HIGH_QUALITY -> 18
    }

    private fun audioKbps(preset: ExportPreset) = when (preset) {
        ExportPreset.SMALL_FILE -> 96
        ExportPreset.BALANCED -> 128
        ExportPreset.HIGH_QUALITY -> 192
    }

    private fun bitsPerPixel(preset: ExportPreset) = when (preset) {
        ExportPreset.SMALL_FILE -> 0.06
        ExportPreset.BALANCED -> 0.10
        ExportPreset.HIGH_QUALITY -> 0.15
    }

    private fun bitrateKbps(r: Resolution, preset: ExportPreset): Int =
        (r.width.toDouble() * r.height * ASSUMED_FPS * bitsPerPixel(preset) / 1000.0).roundToInt().coerceAtLeast(1)

    private companion object {
        const val SMALL_MAX_HEIGHT = 720
        const val ASSUMED_FPS = 30.0
        val FULL_HD = Resolution(1920, 1080)
        val LOSSLESS = setOf(AudioCodec.FLAC, AudioCodec.PCM)
    }
}
