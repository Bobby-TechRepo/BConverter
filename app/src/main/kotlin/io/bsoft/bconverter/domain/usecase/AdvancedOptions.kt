package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoStream
import io.bsoft.bconverter.domain.model.crfRange
import kotlin.math.roundToInt

/** The user's overrides on top of a preset (spec §18). Null means "keep what the preset chose". */
data class AdvancedOptions(
    val quality: VideoQuality? = null,
    /** Target height; the width follows the source's aspect ratio. */
    val resolutionHeight: Int? = null,
    val frameRate: Double? = null,
    val profile: String? = null,
    val level: String? = null,
    val pixelFormat: String? = null,
    val audioBitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
) {
    val isDefault: Boolean get() = this == AdvancedOptions()

    /** Drops every override that is not on offer in [choices] (e.g. after the codec or container changed). */
    fun sanitized(choices: AdvancedChoices): AdvancedOptions = AdvancedOptions(
        quality = when (val q = quality) {
            is VideoQuality.Crf -> q.takeIf { choices.crfRange?.contains(it.value) == true }
            is VideoQuality.Bitrate -> q.takeIf { choices.videoEncoded && it.kbps in 1..AdvancedChoices.MAX_VIDEO_KBPS }
            null -> null
        },
        resolutionHeight = resolutionHeight?.takeIf { it in choices.resolutionHeights },
        frameRate = frameRate?.takeIf { it in choices.frameRates },
        profile = profile?.takeIf { it in choices.profiles },
        level = level?.takeIf { it in choices.levels },
        pixelFormat = pixelFormat?.takeIf { it in choices.pixelFormats },
        audioBitrateKbps = audioBitrateKbps?.takeIf { it in choices.audioBitrates },
        sampleRateHz = sampleRateHz?.takeIf { it in choices.sampleRates },
        channels = channels?.takeIf { it in choices.channelCounts },
    )
}

/** What can be offered for the current source and settings. Everything is empty for a stream that is copied, not encoded. */
data class AdvancedChoices(
    val videoEncoded: Boolean = false,
    val audioEncoded: Boolean = false,
    /** CRF range when the encoder honours CRF; null means bitrate-only. */
    val crfRange: IntRange? = null,
    val resolutionHeights: List<Int> = emptyList(),
    val frameRates: List<Double> = emptyList(),
    val profiles: List<String> = emptyList(),
    val levels: List<String> = emptyList(),
    val pixelFormats: List<String> = emptyList(),
    val audioBitrates: List<Int> = emptyList(),
    val sampleRates: List<Int> = emptyList(),
    val channelCounts: List<Int> = emptyList(),
) {
    companion object {
        const val MAX_VIDEO_KBPS = 500_000
        private val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360, 240)
        private val FRAME_RATES = listOf(60.0, 50.0, 30.0, 25.0, 24.0, 15.0)
        private val AUDIO_KBPS = listOf(64, 96, 128, 160, 192, 256, 320)
        private val SAMPLE_RATES = listOf(22050, 32000, 44100, 48000)
        private val OPUS_RATES = listOf(16000, 24000, 48000)
        private val LOSSLESS = setOf(AudioCodec.FLAC, AudioCodec.PCM)
        private const val FPS_TOLERANCE = 0.1

        fun of(settings: ConversionSettings, metadata: MediaMetadata, crf: Boolean): AdvancedChoices {
            val video = (settings.video as? VideoPlan.Encode)?.settings
            val audio = (settings.audio as? AudioPlan.Encode)?.settings
            val stream = metadata.videoStreams.firstOrNull()
            return AdvancedChoices(
                videoEncoded = video != null,
                audioEncoded = audio != null,
                crfRange = if (video != null && crf) video.codec.crfRange() else null,
                resolutionHeights = if (video != null && stream != null) HEIGHTS.filter { it < stream.displayHeight } else emptyList(),
                frameRates = if (video != null) {
                    val fps = stream?.frameRate
                    FRAME_RATES.filter { fps == null || it <= fps + FPS_TOLERANCE }
                } else emptyList(),
                profiles = if (video != null) profiles(video.codec) else emptyList(),
                levels = if (video != null) levels(video.codec) else emptyList(),
                pixelFormats = if (video != null) pixelFormats(video.codec) else emptyList(),
                audioBitrates = if (audio != null && audio.codec !in LOSSLESS) AUDIO_KBPS else emptyList(),
                sampleRates = if (audio != null) (if (audio.codec == AudioCodec.OPUS) OPUS_RATES else SAMPLE_RATES) else emptyList(),
                channelCounts = if (audio != null) listOf(1, 2) else emptyList(),
            )
        }

        private fun profiles(codec: VideoCodec) = when (codec) {
            VideoCodec.H264 -> listOf("baseline", "main", "high")
            VideoCodec.HEVC -> listOf("main", "main10")
            else -> emptyList()
        }

        private fun levels(codec: VideoCodec) = when (codec) {
            VideoCodec.H264 -> listOf("3.0", "3.1", "4.0", "4.1", "4.2", "5.0", "5.1", "5.2")
            VideoCodec.HEVC -> listOf("3.0", "3.1", "4.0", "4.1", "5.0", "5.1")
            else -> emptyList()
        }

        private fun pixelFormats(codec: VideoCodec) = when (codec) {
            VideoCodec.HEVC, VideoCodec.VP9, VideoCodec.AV1 -> listOf("yuv420p", "yuv420p10le")
            else -> listOf("yuv420p")
        }
    }
}

object AdvancedOptionsApplier {
    /** Applies [options] to the preset's [settings]. Copied streams are never changed. */
    fun apply(settings: ConversionSettings, options: AdvancedOptions, stream: VideoStream?): ConversionSettings {
        if (options.isDefault) return settings
        val video = when (val plan = settings.video) {
            VideoPlan.Copy -> plan
            is VideoPlan.Encode -> VideoPlan.Encode(
                plan.settings.copy(
                    quality = options.quality ?: plan.settings.quality,
                    resolution = options.resolutionHeight.let { h ->
                        if (h == null || stream == null) plan.settings.resolution else scaledTo(h, stream)
                    },
                    frameRate = options.frameRate ?: plan.settings.frameRate,
                    profile = options.profile ?: plan.settings.profile,
                    level = options.level ?: plan.settings.level,
                    pixelFormat = options.pixelFormat ?: plan.settings.pixelFormat,
                ),
            )
        }
        val audio = when (val plan = settings.audio) {
            AudioPlan.None, AudioPlan.Copy -> plan
            is AudioPlan.Encode -> {
                val s = plan.settings
                val lossless = s.codec == AudioCodec.FLAC || s.codec == AudioCodec.PCM
                AudioPlan.Encode(
                    s.copy(
                        bitrateKbps = if (lossless) s.bitrateKbps else options.audioBitrateKbps ?: s.bitrateKbps,
                        sampleRateHz = options.sampleRateHz ?: s.sampleRateHz,
                        channels = options.channels ?: s.channels,
                    ),
                )
            }
        }
        return settings.copy(video = video, audio = audio)
    }

    /** Null when the source is already at or below [height] (no scaling needed) or unknown. */
    private fun scaledTo(height: Int, stream: VideoStream?): Resolution? {
        if (stream == null || height >= stream.displayHeight) return null
        val h = (height / 2 * 2).coerceAtLeast(2)
        val w = ((stream.displayWidth * h.toDouble() / stream.displayHeight) / 2).roundToInt() * 2
        return Resolution(w.coerceAtLeast(2), h)
    }
}
