package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec.*
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.fakes.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionRequestValidatorTest {
    private val validator = ConversionRequestValidator(TestMedia.allEncoders())

    private fun encode(
        quality: VideoQuality = VideoQuality.Crf(23), codec: io.bsoft.bconverter.domain.model.VideoCodec = H264,
        resolution: Resolution? = null, fps: Double? = null,
    ) = VideoPlan.Encode(VideoSettings(codec, quality, resolution, fps))
    private fun aac(bitrate: Int? = 128, codec: io.bsoft.bconverter.domain.model.AudioCodec = AAC, rate: Int? = null, ch: Int? = null) =
        AudioPlan.Encode(AudioSettings(codec, bitrate, rate, ch))
    private fun check(
        video: VideoPlan = encode(), audio: AudioPlan = aac(), container: ContainerFormat = MP4,
        meta: MediaMetadata = TestMedia.metadata(), name: String = "Holiday",
        v: ConversionRequestValidator = validator,
    ) = v.validate(ConversionSettings(container, video, audio), meta, name)

    @Test fun a_normal_configuration_is_valid() = assertTrue(check().isEmpty())

    // ---- source ----
    @Test fun source_without_video_is_rejected() =
        assertEquals(listOf(ConversionIssue.SourceHasNoVideo), check(meta = TestMedia.metadata(video = null)))

    @Test fun audio_requested_but_source_has_none_is_rejected() {
        val meta = TestMedia.metadata(audio = null)
        assertEquals(listOf(ConversionIssue.SourceHasNoAudio), check(meta = meta))
        assertEquals(listOf(ConversionIssue.SourceHasNoAudio), check(audio = AudioPlan.Copy, meta = meta))
        assertTrue(check(audio = AudioPlan.None, meta = meta).isEmpty())
    }

    // ---- video codec ----
    @Test fun codec_not_allowed_in_container() =
        assertEquals(listOf(ConversionIssue.CodecNotAllowedInContainer(H264, WEBM)), check(container = WEBM, audio = aac(codec = OPUS)))

    @Test fun valid_codec_without_an_encoder() {
        val v = ConversionRequestValidator(TestMedia.repo(H264, AAC))
        assertEquals(listOf(ConversionIssue.EncoderUnavailable(HEVC)), check(video = encode(codec = HEVC), v = v))
    }

    @Test fun crf_is_not_supported_for_mpeg4() =
        assertEquals(listOf(ConversionIssue.CrfNotSupported(MPEG4)), check(video = encode(codec = MPEG4), container = AVI, audio = aac(codec = MP3)))

    @Test fun crf_range_depends_on_codec() {
        assertEquals(listOf(ConversionIssue.CrfOutOfRange(H264, 0, 51)), check(video = encode(VideoQuality.Crf(52))))
        assertEquals(listOf(ConversionIssue.CrfOutOfRange(H264, 0, 51)), check(video = encode(VideoQuality.Crf(-1))))
        assertTrue(check(video = encode(VideoQuality.Crf(63), codec = VP9), container = WEBM, audio = aac(codec = OPUS)).isEmpty())
    }

    @Test fun video_bitrate_must_be_positive() =
        assertEquals(listOf(ConversionIssue.InvalidVideoBitrate), check(video = encode(VideoQuality.Bitrate(0))))

    @Test fun resolution_must_be_positive_even_and_reasonable() {
        listOf(Resolution(1281, 720), Resolution(1280, 721), Resolution(0, 720), Resolution(16384, 720)).forEach {
            assertEquals("$it", listOf(ConversionIssue.InvalidResolution), check(video = encode(resolution = it)))
        }
        assertTrue(check(video = encode(resolution = Resolution(640, 360))).isEmpty())
    }

    @Test fun frame_rate_must_be_within_bounds() {
        listOf(0.0, -1.0, 500.0).forEach { assertEquals(listOf(ConversionIssue.InvalidFrameRate), check(video = encode(fps = it))) }
        assertTrue(check(video = encode(fps = 29.97)).isEmpty())
    }

    // ---- video copy ----
    @Test fun video_copy_requires_a_container_compatible_source_codec() {
        assertTrue(check(video = VideoPlan.Copy, container = MKV).isEmpty())
        assertEquals(listOf(ConversionIssue.VideoCopyNotPossible), check(video = VideoPlan.Copy, container = WEBM, audio = AudioPlan.None))
        val unknown = TestMedia.metadata(TestMedia.video(codec = "prores"))
        assertEquals(listOf(ConversionIssue.VideoCopyNotPossible), check(video = VideoPlan.Copy, meta = unknown))
    }

    @Test fun audio_copy_requires_a_container_compatible_source_codec() {
        assertTrue(check(audio = AudioPlan.Copy).isEmpty())
        assertEquals(listOf(ConversionIssue.AudioCopyNotPossible), check(audio = AudioPlan.Copy, container = THREE_GP, meta = TestMedia.metadata(audio = TestMedia.audio("mp3"))))
    }

    // ---- audio ----
    @Test fun audio_codec_not_allowed_in_container() =
        assertEquals(listOf(ConversionIssue.CodecNotAllowedInContainer(AAC, WEBM)), check(video = encode(codec = VP9), container = WEBM))

    @Test fun lossless_audio_rejects_a_bitrate() =
        assertEquals(listOf(ConversionIssue.AudioBitrateNotAllowed(FLAC)), check(container = MKV, audio = aac(codec = FLAC, bitrate = 128)))

    @Test fun audio_bitrate_must_be_in_range() {
        listOf(4, 600).forEach { assertEquals(listOf(ConversionIssue.InvalidAudioBitrate), check(audio = aac(bitrate = it))) }
    }

    @Test fun sample_rate_must_be_a_standard_rate() {
        assertEquals(listOf(ConversionIssue.InvalidSampleRate), check(audio = aac(rate = 12345)))
        assertTrue(check(audio = aac(rate = 44100)).isEmpty())
    }

    @Test fun channel_count_is_limited_per_codec() {
        assertEquals(listOf(ConversionIssue.InvalidChannels(MP3)), check(audio = aac(codec = MP3, ch = 3)))
        assertEquals(listOf(ConversionIssue.InvalidChannels(AAC)), check(audio = aac(ch = 0)))
        assertTrue(check(audio = aac(ch = 6)).isEmpty())
    }

    // ---- output name ----
    @Test fun unsafe_or_empty_output_names_are_rejected() {
        listOf("", "   ", "a/b", "a\\b", "bad:name", "..", "a?b", "x".repeat(201)).forEach {
            assertEquals("'$it'", listOf(ConversionIssue.InvalidOutputName), check(name = it))
        }
    }

    @Test fun ordinary_and_unicode_names_are_accepted() {
        listOf("My Holiday 2024", "Отпуск", "clip.final").forEach { assertTrue(it, check(name = it).isEmpty()) }
    }

    @Test fun all_problems_are_reported_together() {
        val issues = check(video = encode(resolution = Resolution(1, 1).copy(width = 3), fps = 0.0))
        assertEquals(setOf<ConversionIssue>(ConversionIssue.InvalidResolution, ConversionIssue.InvalidFrameRate), issues.toSet())
    }
}
