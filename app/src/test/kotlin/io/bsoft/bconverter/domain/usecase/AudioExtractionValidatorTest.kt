package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.fakes.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExtractionValidatorTest {
    private val validator = AudioExtractionValidator(TestMedia.allEncoders())
    private fun enc(kbps: Int? = null, rate: Int? = null, ch: Int? = null) = AudioExtractionMode.Encode(kbps, rate, ch)
    private fun check(
        format: AudioFormat = AudioFormat.MP3, mode: AudioExtractionMode = enc(192), stream: Int = 0,
        meta: MediaMetadata = TestMedia.metadata(), name: String = "Track", v: AudioExtractionValidator = validator,
    ) = v.validate(AudioExtractionSettings(format, stream, mode), meta, name)

    @Test fun a_normal_encode_is_valid() = assertTrue(check().isEmpty())
    @Test fun a_matching_copy_is_valid() = assertTrue(check(AudioFormat.M4A, AudioExtractionMode.Copy).isEmpty())

    @Test fun a_source_without_audio_is_rejected() =
        assertEquals(listOf(ConversionIssue.SourceHasNoAudio), check(meta = TestMedia.metadata(audio = null)))

    @Test fun a_missing_audio_track_is_rejected() {
        assertEquals(listOf(ConversionIssue.InvalidAudioStream), check(stream = 5))
        assertEquals(listOf(ConversionIssue.InvalidAudioStream), check(stream = -1))
    }

    @Test fun copy_requires_the_matching_codec() =
        assertEquals(listOf(ConversionIssue.AudioCopyNotPossible), check(AudioFormat.MP3, AudioExtractionMode.Copy))

    @Test fun a_missing_encoder_is_reported() =
        assertEquals(listOf(ConversionIssue.EncoderUnavailable(MP3)), check(v = AudioExtractionValidator(TestMedia.repo(AAC))))

    @Test fun lossless_formats_reject_a_bitrate() =
        assertEquals(listOf(ConversionIssue.AudioBitrateNotAllowed(FLAC)), check(AudioFormat.FLAC, enc(kbps = 128)))

    @Test fun bitrate_limits_depend_on_the_codec() {
        assertEquals(listOf(ConversionIssue.InvalidAudioBitrate), check(mode = enc(kbps = 400)))
        assertEquals(listOf(ConversionIssue.InvalidAudioBitrate), check(mode = enc(kbps = 4)))
        assertTrue(check(mode = enc(kbps = 320)).isEmpty())
        assertTrue(check(AudioFormat.M4A, enc(kbps = 512)).isEmpty())
    }

    @Test fun sample_rate_limits_depend_on_the_codec() {
        assertEquals(listOf(ConversionIssue.InvalidSampleRate), check(mode = enc(rate = 12345)))
        assertEquals(listOf(ConversionIssue.InvalidSampleRate), check(AudioFormat.OPUS, enc(rate = 44100)))
        assertEquals(listOf(ConversionIssue.InvalidSampleRate), check(AudioFormat.MP3, enc(rate = 96000)))
        assertTrue(check(AudioFormat.OPUS, enc(rate = 48000)).isEmpty())
        assertTrue(check(AudioFormat.M4A, enc(rate = 96000)).isEmpty())
    }

    @Test fun channel_limits_depend_on_the_codec() {
        assertEquals(listOf(ConversionIssue.InvalidChannels(MP3)), check(mode = enc(ch = 3)))
        assertEquals(listOf(ConversionIssue.InvalidChannels(MP3)), check(mode = enc(ch = 0)))
        assertTrue(check(AudioFormat.OPUS, enc(ch = 6)).isEmpty())
    }

    @Test fun unsafe_output_names_are_rejected() =
        listOf("", "a/b", "bad:name", "..").forEach { assertEquals("'$it'", listOf(ConversionIssue.InvalidOutputName), check(name = it)) }
}
