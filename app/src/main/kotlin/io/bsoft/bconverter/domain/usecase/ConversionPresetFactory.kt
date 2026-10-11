package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerCompatibility
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.CodecIds
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.model.VideoStream
import io.bsoft.bconverter.domain.model.crfRange
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository
import kotlin.math.roundToInt

/**
 * Turns a preset into concrete settings for a source and target container, using only encoders that exist.
 * Returns null when no valid, encodable combination exists.
 *
 * | Preset   | CRF | Max height | Audio kbps |
 * |----------|-----|------------|------------|
 * | Small    | 28  | 720        | 96         |
 * | Balanced | 23  | original   | 128        |
 * | High     | 18  | original   | 192        |
 * | Original | copy streams if the container accepts them, else CRF 16 and 256 kbps audio |
 */
class ConversionPresetFactory(private val capabilities: MediaCapabilityRepository) {

    fun create(preset: ConversionPreset, metadata: MediaMetadata, container: ContainerFormat): ConversionSettings? {
        val videoStream = metadata.videoStreams.firstOrNull() ?: return null
        val video = videoPlan(preset, videoStream, container) ?: return null
        val audio = audioPlan(preset, metadata, container) ?: return null
        return ConversionSettings(container, video, audio)
    }

    private fun videoPlan(preset: ConversionPreset, stream: VideoStream, container: ContainerFormat): VideoPlan? {
        if (preset == ConversionPreset.ORIGINAL_QUALITY) {
            val source = CodecIds.video(stream.codec)
            if (source != null && source in ContainerCompatibility.videoCodecs(container)) return VideoPlan.Copy
        }
        val available = capabilities.getVideoCodecs(container)
        val codec = VIDEO_PREFERENCE.firstOrNull { it in available } ?: return null
        val quality = if (codec.crfRange() != null && capabilities.supportsCrf(codec)) {
            VideoQuality.Crf(crf(preset))
        } else {
            VideoQuality.Bitrate(bitrateKbps(preset, stream))
        }
        val resolution = if (preset == ConversionPreset.SMALL_FILE) capped(stream) else null
        return VideoPlan.Encode(VideoSettings(codec, quality, resolution))
    }

    private fun audioPlan(preset: ConversionPreset, metadata: MediaMetadata, container: ContainerFormat): AudioPlan? {
        val stream = metadata.audioStreams.firstOrNull() ?: return AudioPlan.None
        if (preset == ConversionPreset.ORIGINAL_QUALITY) {
            val source = CodecIds.audio(stream.codec)
            if (source != null && source in ContainerCompatibility.audioCodecs(container)) return AudioPlan.Copy
        }
        val available = capabilities.getAudioCodecs(container)
        val codec = AUDIO_PREFERENCE.firstOrNull { it in available } ?: return null
        val bitrate = if (codec in LOSSLESS) null else audioKbps(preset)
        return AudioPlan.Encode(AudioSettings(codec, bitrateKbps = bitrate))
    }

    private fun crf(preset: ConversionPreset) = when (preset) {
        ConversionPreset.SMALL_FILE -> 28
        ConversionPreset.BALANCED -> 23
        ConversionPreset.HIGH_QUALITY -> 18
        ConversionPreset.ORIGINAL_QUALITY -> 16
    }

    private fun audioKbps(preset: ConversionPreset) = when (preset) {
        ConversionPreset.SMALL_FILE -> 96
        ConversionPreset.BALANCED -> 128
        ConversionPreset.HIGH_QUALITY -> 192
        ConversionPreset.ORIGINAL_QUALITY -> 256
    }

    /** For bitrate-only codecs: a share of the source video bitrate, or a flat default when it is unknown. */
    private fun bitrateKbps(preset: ConversionPreset, stream: VideoStream): Int {
        val sourceKbps = stream.bitrateBps?.let { it / 1000.0 } ?: return DEFAULT_BITRATE_KBPS
        val factor = when (preset) {
            ConversionPreset.SMALL_FILE -> 0.4
            ConversionPreset.BALANCED -> 0.7
            else -> 1.0
        }
        return (sourceKbps * factor).roundToInt().coerceAtLeast(1)
    }

    /** Scales down to [SMALL_MAX_HEIGHT] by display height (rotation-aware); never upscales. */
    private fun capped(stream: VideoStream): Resolution? {
        val h = stream.displayHeight
        if (h <= SMALL_MAX_HEIGHT) return null
        val width = stream.displayWidth * SMALL_MAX_HEIGHT.toDouble() / h
        return Resolution(((width / 2).roundToInt() * 2).coerceAtLeast(2), SMALL_MAX_HEIGHT)
    }

    private companion object {
        const val SMALL_MAX_HEIGHT = 720
        const val DEFAULT_BITRATE_KBPS = 2000
        val VIDEO_PREFERENCE = listOf(
            VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9, VideoCodec.AV1,
            VideoCodec.VP8, VideoCodec.MPEG4, VideoCodec.MPEG2,
        )
        val AUDIO_PREFERENCE = listOf(
            AudioCodec.AAC, AudioCodec.OPUS, AudioCodec.MP3, AudioCodec.AC3,
            AudioCodec.VORBIS, AudioCodec.FLAC, AudioCodec.PCM,
        )
        val LOSSLESS = setOf(AudioCodec.FLAC, AudioCodec.PCM)
    }
}
