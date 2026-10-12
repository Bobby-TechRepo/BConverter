package io.bsoft.bconverter.domain.model

/** Output formats for audio extraction (spec §19), in display order. */
enum class AudioFormat(val extension: String, val mimeType: String, val codec: AudioCodec) {
    MP3("mp3", "audio/mpeg", AudioCodec.MP3),
    AAC("aac", "audio/aac", AudioCodec.AAC),
    M4A("m4a", "audio/mp4", AudioCodec.AAC),
    FLAC("flac", "audio/flac", AudioCodec.FLAC),
    WAV("wav", "audio/x-wav", AudioCodec.PCM),
    OPUS("opus", "audio/ogg", AudioCodec.OPUS);

    val isLossless: Boolean get() = codec == AudioCodec.FLAC || codec == AudioCodec.PCM
}

sealed interface AudioExtractionMode {
    /** Stream copy: no re-encoding, only possible when the source codec matches the format's codec. */
    data object Copy : AudioExtractionMode
    data class Encode(
        val bitrateKbps: Int? = null,
        val sampleRateHz: Int? = null,
        val channels: Int? = null,
    ) : AudioExtractionMode
}

/** [streamOrdinal] is the position among the source's audio streams (0 = first audio stream). */
data class AudioExtractionSettings(val format: AudioFormat, val streamOrdinal: Int, val mode: AudioExtractionMode)
