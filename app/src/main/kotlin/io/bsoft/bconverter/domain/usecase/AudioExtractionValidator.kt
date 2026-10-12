package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.CodecIds
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository

/** Reports every problem with an audio-extraction configuration; empty means valid. */
class AudioExtractionValidator(private val capabilities: MediaCapabilityRepository) {

    fun validate(settings: AudioExtractionSettings, metadata: MediaMetadata, outputName: String): List<ConversionIssue> = buildList {
        val stream = metadata.audioStreams.getOrNull(settings.streamOrdinal)
        if (!metadata.hasAudio) add(ConversionIssue.SourceHasNoAudio)
        else if (stream == null) add(ConversionIssue.InvalidAudioStream)

        when (val mode = settings.mode) {
            AudioExtractionMode.Copy -> if (stream != null && CodecIds.audio(stream.codec) != settings.format.codec) {
                add(ConversionIssue.AudioCopyNotPossible)
            }
            is AudioExtractionMode.Encode -> validateEncode(settings.format.codec, mode)
        }
        if (!OutputNames.isValid(outputName)) add(ConversionIssue.InvalidOutputName)
    }

    private fun MutableList<ConversionIssue>.validateEncode(codec: AudioCodec, mode: AudioExtractionMode.Encode) {
        if (!capabilities.canEncodeAudio(codec)) add(ConversionIssue.EncoderUnavailable(codec))
        val kbps = mode.bitrateKbps
        if (kbps != null) {
            if (codec in LOSSLESS) add(ConversionIssue.AudioBitrateNotAllowed(codec))
            else if (kbps !in 8..(if (codec == AudioCodec.MP3) 320 else 512)) add(ConversionIssue.InvalidAudioBitrate)
        }
        val rate = mode.sampleRateHz
        if (rate != null && rate !in allowedSampleRates(codec)) add(ConversionIssue.InvalidSampleRate)
        val channels = mode.channels
        if (channels != null && channels !in 1..(if (codec == AudioCodec.MP3) 2 else 8)) add(ConversionIssue.InvalidChannels(codec))
    }

    private fun allowedSampleRates(codec: AudioCodec): Set<Int> = when (codec) {
        AudioCodec.OPUS -> setOf(8000, 12000, 16000, 24000, 48000)
        AudioCodec.MP3 -> COMMON_RATES.filter { it <= 48000 }.toSet()
        else -> COMMON_RATES
    }

    private companion object {
        val LOSSLESS = setOf(AudioCodec.FLAC, AudioCodec.PCM)
        val COMMON_RATES = setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 88200, 96000)
    }
}
