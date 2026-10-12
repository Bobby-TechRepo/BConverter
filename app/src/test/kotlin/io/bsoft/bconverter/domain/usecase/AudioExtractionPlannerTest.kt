package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.fakes.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExtractionPlannerTest {
    private val all = AudioExtractionPlanner(TestMedia.allEncoders())
    private val none = AudioExtractionPlanner(TestMedia.repo())
    private val aac = TestMedia.metadata()
    private val vorbis = TestMedia.metadata(audio = TestMedia.audio("vorbis"))
    private fun encode(f: AudioFormat, kbps: Int? = null, stream: Int = 0) =
        AudioExtractionSettings(f, stream, AudioExtractionMode.Encode(bitrateKbps = kbps))
    private fun copy(f: AudioFormat, stream: Int = 0) = AudioExtractionSettings(f, stream, AudioExtractionMode.Copy)

    // ---- which formats are offered ----
    @Test fun every_format_is_offered_when_all_encoders_exist() = assertEquals(AudioFormat.entries, all.availableFormats(aac, 0))

    @Test fun without_encoders_only_copy_compatible_formats_are_offered() =
        assertEquals(listOf(AudioFormat.AAC, AudioFormat.M4A), none.availableFormats(aac, 0))

    @Test fun nothing_is_offered_without_encoders_for_an_uncopyable_source() = assertTrue(none.availableFormats(vorbis, 0).isEmpty())

    @Test fun copy_is_possible_only_for_the_matching_codec() {
        assertTrue(all.canCopy(AudioFormat.M4A, aac, 0)); assertTrue(all.canCopy(AudioFormat.AAC, aac, 0))
        assertFalse(all.canCopy(AudioFormat.MP3, aac, 0)); assertFalse(all.canCopy(AudioFormat.M4A, aac, 3))
    }

    // ---- defaults ----
    @Test fun a_copyable_source_defaults_to_copy_in_the_best_matching_format() = assertEquals(copy(AudioFormat.M4A), all.defaultSettings(aac, 0))

    @Test fun an_uncopyable_source_defaults_to_mp3_at_192() = assertEquals(encode(AudioFormat.MP3, 192), all.defaultSettings(vorbis, 0))

    @Test fun the_encode_default_falls_back_to_what_is_installed() =
        assertEquals(encode(AudioFormat.M4A, 192), AudioExtractionPlanner(TestMedia.repo(AAC)).defaultSettings(vorbis, 0))

    @Test fun no_default_when_nothing_is_possible() = assertNull(none.defaultSettings(vorbis, 0))

    @Test fun copy_defaults_work_without_any_encoder() = assertEquals(copy(AudioFormat.M4A), none.defaultSettings(aac, 0))

    // ---- settings for a chosen format ----
    @Test fun a_matching_format_copies_by_default() = assertEquals(copy(AudioFormat.AAC), all.settingsFor(AudioFormat.AAC, aac, 0))

    @Test fun copy_can_be_declined() = assertEquals(encode(AudioFormat.M4A, 192), all.settingsFor(AudioFormat.M4A, aac, 0, preferCopy = false))

    @Test fun declining_copy_falls_back_to_copy_when_no_encoder_exists() =
        assertEquals(copy(AudioFormat.M4A), none.settingsFor(AudioFormat.M4A, aac, 0, preferCopy = false))

    @Test fun a_different_codec_is_encoded_at_the_format_default_bitrate() {
        assertEquals(encode(AudioFormat.MP3, 192), all.settingsFor(AudioFormat.MP3, aac, 0))
        assertEquals(encode(AudioFormat.OPUS, 128), all.settingsFor(AudioFormat.OPUS, aac, 0))
    }

    @Test fun lossless_formats_have_no_bitrate() {
        assertEquals(encode(AudioFormat.FLAC, null), all.settingsFor(AudioFormat.FLAC, aac, 0))
        assertEquals(encode(AudioFormat.WAV, null), all.settingsFor(AudioFormat.WAV, aac, 0))
    }

    @Test fun an_impossible_format_gives_no_settings() = assertNull(none.settingsFor(AudioFormat.MP3, aac, 0))

    @Test fun the_chosen_audio_stream_is_respected() {
        val two = MediaMetadata(10_000, ContainerFormat.MKV, audioStreams = listOf(AudioStream(1, "aac", 48000, 2), AudioStream(2, "mp3", 44100, 2)))
        assertEquals(copy(AudioFormat.MP3, stream = 1), all.settingsFor(AudioFormat.MP3, two, 1))
        assertEquals(encode(AudioFormat.MP3, 192, stream = 0), all.settingsFor(AudioFormat.MP3, two, 0))
    }
}
