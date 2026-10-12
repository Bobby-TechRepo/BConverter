package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.CodecIds
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository

/**
 * Decides which output formats make sense for a source audio stream and how to produce them:
 * stream copy when the codec already matches (no quality loss, works even without encoders), otherwise re-encode.
 */
class AudioExtractionPlanner(private val capabilities: MediaCapabilityRepository) {

    fun canCopy(format: AudioFormat, metadata: MediaMetadata, streamOrdinal: Int): Boolean {
        val source = CodecIds.audio(metadata.audioStreams.getOrNull(streamOrdinal)?.codec) ?: return false
        return source == format.codec
    }

    fun availableFormats(metadata: MediaMetadata, streamOrdinal: Int): List<AudioFormat> =
        AudioFormat.entries.filter { canCopy(it, metadata, streamOrdinal) || capabilities.canEncodeAudio(it.codec) }

    /** Null when the format is neither copyable nor encodable. Copy is preferred unless declined (and an encoder exists). */
    fun settingsFor(format: AudioFormat, metadata: MediaMetadata, streamOrdinal: Int, preferCopy: Boolean = true): AudioExtractionSettings? {
        val copyOk = canCopy(format, metadata, streamOrdinal)
        val encodeOk = capabilities.canEncodeAudio(format.codec)
        val mode = when {
            copyOk && (preferCopy || !encodeOk) -> AudioExtractionMode.Copy
            encodeOk -> AudioExtractionMode.Encode(bitrateKbps = defaultBitrateKbps(format))
            else -> return null
        }
        return AudioExtractionSettings(format, streamOrdinal, mode)
    }

    fun defaultSettings(metadata: MediaMetadata, streamOrdinal: Int): AudioExtractionSettings? {
        COPY_PREFERENCE.firstOrNull { canCopy(it, metadata, streamOrdinal) }
            ?.let { return settingsFor(it, metadata, streamOrdinal, preferCopy = true) }
        ENCODE_PREFERENCE.firstOrNull { capabilities.canEncodeAudio(it.codec) }
            ?.let { return settingsFor(it, metadata, streamOrdinal, preferCopy = false) }
        return null
    }

    private fun defaultBitrateKbps(format: AudioFormat): Int? = when (format) {
        AudioFormat.MP3, AudioFormat.AAC, AudioFormat.M4A -> 192
        AudioFormat.OPUS -> 128
        AudioFormat.FLAC, AudioFormat.WAV -> null
    }

    private companion object {
        val COPY_PREFERENCE = listOf(AudioFormat.M4A, AudioFormat.MP3, AudioFormat.FLAC, AudioFormat.OPUS, AudioFormat.WAV, AudioFormat.AAC)
        val ENCODE_PREFERENCE = listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.FLAC, AudioFormat.WAV, AudioFormat.AAC)
    }
}
