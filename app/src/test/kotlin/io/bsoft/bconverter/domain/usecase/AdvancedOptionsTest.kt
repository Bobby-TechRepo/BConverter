package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.model.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedOptionsTest {
    private val stream = VideoStream(0, "h264", 1920, 1080, 30.0)
    private fun video(codec: VideoCodec = VideoCodec.H264, q: VideoQuality = VideoQuality.Crf(23), res: Resolution? = null) =
        VideoPlan.Encode(VideoSettings(codec, q, res))
    private fun settings(v: VideoPlan = video(), a: AudioPlan = AudioPlan.Encode(AudioSettings(AudioCodec.AAC, 128))) =
        ConversionSettings(ContainerFormat.MP4, v, a)
    private fun AdvancedOptions.on(s: ConversionSettings = settings()) = AdvancedOptionsApplier.apply(s, this, stream)
    private fun ConversionSettings.v() = (video as VideoPlan.Encode).settings
    private fun ConversionSettings.a() = (audio as AudioPlan.Encode).settings

    // ---- apply ----
    @Test fun no_options_change_nothing() {
        assertTrue(AdvancedOptions().isDefault); assertEquals(settings(), AdvancedOptions().on())
    }

    @Test fun a_quality_override_replaces_the_presets_quality() {
        assertEquals(VideoQuality.Crf(18), AdvancedOptions(quality = VideoQuality.Crf(18)).on().v().quality)
        assertEquals(VideoQuality.Bitrate(3000), AdvancedOptions(quality = VideoQuality.Bitrate(3000)).on().v().quality)
    }

    @Test fun a_lower_height_scales_keeping_the_aspect_ratio_with_even_sizes() {
        assertEquals(Resolution(1280, 720), AdvancedOptions(resolutionHeight = 720).on().v().resolution)
        assertEquals(Resolution(854, 480), AdvancedOptions(resolutionHeight = 480).on().v().resolution)
    }

    @Test fun the_display_orientation_is_used_for_rotated_sources() {
        val rotated = VideoStream(0, "h264", 1920, 1080, 30.0, rotationDegrees = 90) // shown 1080x1920
        val r = AdvancedOptionsApplier.apply(settings(), AdvancedOptions(resolutionHeight = 960), rotated).v().resolution
        assertEquals(Resolution(540, 960), r)
    }

    @Test fun the_source_height_or_more_means_no_scaling_so_a_presets_cap_is_lifted() {
        val capped = settings(video(res = Resolution(1280, 720)))
        assertNull(AdvancedOptions(resolutionHeight = 1080).on(capped).v().resolution)
        assertNull(AdvancedOptions(resolutionHeight = 2160).on().v().resolution)
    }

    @Test fun frame_rate_profile_level_and_pixel_format_are_set() {
        val v = AdvancedOptions(frameRate = 24.0, profile = "high", level = "4.1", pixelFormat = "yuv420p").on().v()
        assertEquals(24.0, v.frameRate); assertEquals("high", v.profile); assertEquals("4.1", v.level); assertEquals("yuv420p", v.pixelFormat)
    }

    @Test fun a_preset_that_copies_the_video_ignores_video_options() {
        val copy = settings(v = VideoPlan.Copy)
        assertEquals(VideoPlan.Copy, AdvancedOptions(resolutionHeight = 720, quality = VideoQuality.Crf(10)).on(copy).video)
    }

    @Test fun audio_options_are_set_on_an_encoded_track() {
        val a = AdvancedOptions(audioBitrateKbps = 192, sampleRateHz = 48000, channels = 2).on().a()
        assertEquals(192, a.bitrateKbps); assertEquals(48000, a.sampleRateHz); assertEquals(2, a.channels)
    }

    @Test fun a_lossless_codec_never_gets_a_bitrate() {
        val flac = settings(a = AudioPlan.Encode(AudioSettings(AudioCodec.FLAC)))
        assertNull(AdvancedOptions(audioBitrateKbps = 192, sampleRateHz = 44100).on(flac).a().bitrateKbps)
        assertEquals(44100, AdvancedOptions(sampleRateHz = 44100).on(flac).a().sampleRateHz)
    }

    @Test fun copied_or_missing_audio_ignores_audio_options() {
        assertEquals(AudioPlan.Copy, AdvancedOptions(audioBitrateKbps = 64).on(settings(a = AudioPlan.Copy)).audio)
        assertEquals(AudioPlan.None, AdvancedOptions(channels = 1).on(settings(a = AudioPlan.None)).audio)
    }

    // ---- choices ----
    private fun meta(fps: Double? = 30.0, h: Int = 1080, w: Int = 1920, audio: Boolean = true) = MediaMetadata(
        10_000, ContainerFormat.MP4, listOf(VideoStream(0, "h264", w, h, fps)),
        if (audio) listOf(AudioStream(1, "aac", 44100, 2)) else emptyList(),
    )

    @Test fun heights_offered_are_only_smaller_than_the_source() {
        assertEquals(listOf(720, 480, 360, 240), AdvancedChoices.of(settings(), meta(h = 1080, w = 1920), crf = true).resolutionHeights.filter { it < 1080 })
        assertFalse(AdvancedChoices.of(settings(), meta(h = 1080), crf = true).resolutionHeights.any { it >= 1080 })
        assertTrue(AdvancedChoices.of(settings(), meta(h = 240, w = 426), crf = true).resolutionHeights.isEmpty())
    }

    @Test fun frame_rates_offered_do_not_exceed_the_source() {
        assertEquals(listOf(30.0, 25.0, 24.0, 15.0), AdvancedChoices.of(settings(), meta(fps = 30.0), crf = true).frameRates)
        assertTrue(AdvancedChoices.of(settings(), meta(fps = 10.0), crf = true).frameRates.isEmpty())
        assertTrue(AdvancedChoices.of(settings(), meta(fps = null), crf = true).frameRates.contains(60.0))
    }

    @Test fun crf_is_offered_only_when_the_encoder_honours_it() {
        assertEquals(0..51, AdvancedChoices.of(settings(), meta(), crf = true).crfRange)
        assertNull(AdvancedChoices.of(settings(), meta(), crf = false).crfRange)
        assertEquals(0..63, AdvancedChoices.of(settings(video(VideoCodec.VP9)), meta(), crf = true).crfRange)
        assertNull(AdvancedChoices.of(settings(video(VideoCodec.MPEG4)), meta(), crf = true).crfRange)
    }

    @Test fun profiles_levels_and_pixel_formats_depend_on_the_codec() {
        val h264 = AdvancedChoices.of(settings(), meta(), crf = true)
        assertEquals(listOf("baseline", "main", "high"), h264.profiles); assertTrue("4.1" in h264.levels); assertEquals(listOf("yuv420p"), h264.pixelFormats)
        val hevc = AdvancedChoices.of(settings(video(VideoCodec.HEVC)), meta(), crf = true)
        assertEquals(listOf("main", "main10"), hevc.profiles); assertTrue("yuv420p10le" in hevc.pixelFormats)
        val vp8 = AdvancedChoices.of(settings(video(VideoCodec.VP8)), meta(), crf = false)
        assertTrue(vp8.profiles.isEmpty() && vp8.levels.isEmpty())
    }

    @Test fun nothing_video_related_is_offered_when_the_video_is_copied() {
        val c = AdvancedChoices.of(settings(v = VideoPlan.Copy), meta(), crf = true)
        assertFalse(c.videoEncoded); assertTrue(c.resolutionHeights.isEmpty() && c.frameRates.isEmpty() && c.profiles.isEmpty()); assertNull(c.crfRange)
    }

    @Test fun audio_choices_follow_the_codec() {
        val aac = AdvancedChoices.of(settings(), meta(), crf = true)
        assertTrue(aac.audioEncoded); assertTrue(128 in aac.audioBitrates); assertTrue(44100 in aac.sampleRates); assertEquals(listOf(1, 2), aac.channelCounts)
        val flac = AdvancedChoices.of(settings(a = AudioPlan.Encode(AudioSettings(AudioCodec.FLAC))), meta(), crf = true)
        assertTrue(flac.audioBitrates.isEmpty()); assertTrue(flac.sampleRates.isNotEmpty())
        val opus = AdvancedChoices.of(settings(a = AudioPlan.Encode(AudioSettings(AudioCodec.OPUS, 96))), meta(), crf = true)
        assertTrue(44100 !in opus.sampleRates && 48000 in opus.sampleRates)
    }

    @Test fun no_audio_choices_without_encoded_audio() {
        val c = AdvancedChoices.of(settings(a = AudioPlan.None), meta(audio = false), crf = true)
        assertFalse(c.audioEncoded); assertTrue(c.audioBitrates.isEmpty() && c.sampleRates.isEmpty() && c.channelCounts.isEmpty())
    }

    // ---- sanitized ----
    @Test fun values_no_longer_on_offer_are_dropped_and_the_rest_kept() {
        val choices = AdvancedChoices.of(settings(video(VideoCodec.VP8)), meta(), crf = false)
        val o = AdvancedOptions(
            quality = VideoQuality.Crf(20), resolutionHeight = 720, profile = "high", level = "4.1", pixelFormat = "yuv420p10le",
            audioBitrateKbps = 128, sampleRateHz = 12345,
        ).sanitized(choices)
        assertNull(o.quality); assertEquals(720, o.resolutionHeight); assertNull(o.profile); assertNull(o.level); assertNull(o.pixelFormat)
        assertEquals(128, o.audioBitrateKbps); assertNull(o.sampleRateHz)
    }

    @Test fun a_bitrate_override_survives_but_crf_must_be_in_range() {
        val c = AdvancedChoices.of(settings(), meta(), crf = true)
        assertEquals(VideoQuality.Bitrate(2500), AdvancedOptions(quality = VideoQuality.Bitrate(2500)).sanitized(c).quality)
        assertNull(AdvancedOptions(quality = VideoQuality.Crf(60)).sanitized(c).quality)
        assertEquals(VideoQuality.Crf(51), AdvancedOptions(quality = VideoQuality.Crf(51)).sanitized(c).quality)
    }

    @Test fun nothing_survives_when_the_stream_is_copied() {
        val c = AdvancedChoices.of(settings(v = VideoPlan.Copy, a = AudioPlan.Copy), meta(), crf = true)
        assertTrue(AdvancedOptions(resolutionHeight = 720, frameRate = 24.0, audioBitrateKbps = 128, channels = 1).sanitized(c).isDefault)
    }
}
