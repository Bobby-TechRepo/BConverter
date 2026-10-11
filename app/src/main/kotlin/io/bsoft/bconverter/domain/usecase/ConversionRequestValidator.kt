package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecIds
import io.bsoft.bconverter.domain.model.ContainerCompatibility
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.crfRange
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository

/** Reports every problem with a conversion configuration; an empty list means it is valid. */
class ConversionRequestValidator(private val capabilities: MediaCapabilityRepository) {

    fun validate(settings: ConversionSettings, metadata: MediaMetadata, outputName: String): List<ConversionIssue> =
        buildList {
            if (!metadata.hasVideo) add(ConversionIssue.SourceHasNoVideo)
            validateVideo(settings, metadata)
            validateAudio(settings, metadata)
            if (!isValidName(outputName)) add(ConversionIssue.InvalidOutputName)
        }

    private fun MutableList<ConversionIssue>.validateVideo(settings: ConversionSettings, metadata: MediaMetadata) {
        when (val plan = settings.video) {
            VideoPlan.Copy -> if (metadata.hasVideo) {
                val source = CodecIds.video(metadata.videoStreams.first().codec)
                if (source == null || source !in ContainerCompatibility.videoCodecs(settings.container)) {
                    add(ConversionIssue.VideoCopyNotPossible)
                }
            }
            is VideoPlan.Encode -> {
                val s = plan.settings
                checkCodec(s.codec, settings.container, ContainerCompatibility.videoCodecs(settings.container),
                    capabilities.getVideoCodecs(settings.container))
                when (val q = s.quality) {
                    is VideoQuality.Crf -> {
                        val range = s.codec.crfRange()
                        if (range == null || !capabilities.supportsCrf(s.codec)) add(ConversionIssue.CrfNotSupported(s.codec))
                        else if (q.value !in range) add(ConversionIssue.CrfOutOfRange(s.codec, range.first, range.last))
                    }
                    is VideoQuality.Bitrate -> if (q.kbps !in 1..MAX_VIDEO_KBPS) add(ConversionIssue.InvalidVideoBitrate)
                }
                val resolution = s.resolution
                if (resolution != null && !isValid(resolution)) add(ConversionIssue.InvalidResolution)
                val fps = s.frameRate
                if (fps != null && !(fps.isFinite() && fps > 0 && fps <= MAX_FPS)) add(ConversionIssue.InvalidFrameRate)
            }
        }
    }

    private fun MutableList<ConversionIssue>.validateAudio(settings: ConversionSettings, metadata: MediaMetadata) {
        when (val plan = settings.audio) {
            AudioPlan.None -> Unit
            AudioPlan.Copy -> if (!metadata.hasAudio) {
                add(ConversionIssue.SourceHasNoAudio)
            } else {
                val source = CodecIds.audio(metadata.audioStreams.first().codec)
                if (source == null || source !in ContainerCompatibility.audioCodecs(settings.container)) {
                    add(ConversionIssue.AudioCopyNotPossible)
                }
            }
            is AudioPlan.Encode -> {
                if (!metadata.hasAudio) add(ConversionIssue.SourceHasNoAudio)
                val s = plan.settings
                checkCodec(s.codec, settings.container, ContainerCompatibility.audioCodecs(settings.container),
                    capabilities.getAudioCodecs(settings.container))
                val kbps = s.bitrateKbps
                if (kbps != null) {
                    if (s.codec in LOSSLESS) add(ConversionIssue.AudioBitrateNotAllowed(s.codec))
                    else if (kbps !in AUDIO_KBPS) add(ConversionIssue.InvalidAudioBitrate)
                }
                val rate = s.sampleRateHz
                if (rate != null && rate !in SAMPLE_RATES) add(ConversionIssue.InvalidSampleRate)
                val channels = s.channels
                if (channels != null) {
                    val max = if (s.codec == AudioCodec.MP3) 2 else 8
                    if (channels !in 1..max) add(ConversionIssue.InvalidChannels(s.codec))
                }
            }
        }
    }

    private fun MutableList<ConversionIssue>.checkCodec(
        codec: Codec, container: ContainerFormat, allowed: Set<Codec>, encodable: List<Codec>,
    ) {
        if (codec !in allowed) add(ConversionIssue.CodecNotAllowedInContainer(codec, container))
        else if (codec !in encodable) add(ConversionIssue.EncoderUnavailable(codec))
    }

    private fun isValid(r: Resolution) =
        r.width in 1..MAX_DIMENSION && r.height in 1..MAX_DIMENSION && r.width % 2 == 0 && r.height % 2 == 0

    private fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH || trimmed.all { it == '.' }) return false
        return name.none { it in FORBIDDEN_NAME_CHARS || it.isISOControl() }
    }

    private companion object {
        const val MAX_VIDEO_KBPS = 500_000
        const val MAX_FPS = 240.0
        const val MAX_DIMENSION = 8192
        const val MAX_NAME_LENGTH = 200
        const val FORBIDDEN_NAME_CHARS = "/\\:*?\"<>|"
        val AUDIO_KBPS = 8..512
        val LOSSLESS = setOf(AudioCodec.FLAC, AudioCodec.PCM)
        val SAMPLE_RATES = setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 88200, 96000)
    }
}
