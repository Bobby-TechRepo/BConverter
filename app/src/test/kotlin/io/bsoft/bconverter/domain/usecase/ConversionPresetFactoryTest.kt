package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec.*
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.fakes.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversionPresetFactoryTest {
    private val factory = ConversionPresetFactory(TestMedia.allEncoders())
    private val source = TestMedia.metadata()

    private fun video(s: VideoSettings) = VideoPlan.Encode(s)
    private fun audio(s: AudioSettings) = AudioPlan.Encode(s)

    @Test fun balanced_keeps_resolution_with_crf_23_and_128k_audio() {
        val s = factory.create(ConversionPreset.BALANCED, source, MP4)!!
        assertEquals(MP4, s.container)
        assertEquals(video(VideoSettings(H264, VideoQuality.Crf(23))), s.video)
        assertEquals(audio(AudioSettings(AAC, bitrateKbps = 128)), s.audio)
    }

    @Test fun high_quality_uses_crf_18_and_192k_audio() {
        val s = factory.create(ConversionPreset.HIGH_QUALITY, source, MP4)!!
        assertEquals(video(VideoSettings(H264, VideoQuality.Crf(18))), s.video)
        assertEquals(audio(AudioSettings(AAC, bitrateKbps = 192)), s.audio)
    }

    @Test fun small_file_uses_crf_28_96k_audio_and_caps_height_at_720() {
        val s = factory.create(ConversionPreset.SMALL_FILE, source, MP4)!!
        assertEquals(video(VideoSettings(H264, VideoQuality.Crf(28), resolution = Resolution(1280, 720))), s.video)
        assertEquals(audio(AudioSettings(AAC, bitrateKbps = 96)), s.audio)
    }

    @Test fun small_file_never_upscales() {
        val s = factory.create(ConversionPreset.SMALL_FILE, TestMedia.metadata(TestMedia.video(w = 640, h = 360)), MP4)!!
        assertNull((s.video as VideoPlan.Encode).settings.resolution)
    }

    @Test fun small_file_rounds_scaled_width_to_an_even_number() {
        val s = factory.create(ConversionPreset.SMALL_FILE, TestMedia.metadata(TestMedia.video(w = 1366, h = 768)), MP4)!!
        assertEquals(Resolution(1280, 720), (s.video as VideoPlan.Encode).settings.resolution)
    }

    @Test fun small_file_scales_by_display_height_for_rotated_video() {
        val rotated = TestMedia.metadata(TestMedia.video(w = 1920, h = 1280, rotation = 90))
        val s = factory.create(ConversionPreset.SMALL_FILE, rotated, MP4)!!
        assertEquals(Resolution(480, 720), (s.video as VideoPlan.Encode).settings.resolution)
    }

    @Test fun original_quality_copies_streams_when_container_accepts_them() {
        val s = factory.create(ConversionPreset.ORIGINAL_QUALITY, source, MKV)!!
        assertEquals(VideoPlan.Copy, s.video); assertEquals(AudioPlan.Copy, s.audio)
    }

    @Test fun original_quality_reencodes_at_crf_16_when_copy_is_impossible() {
        val s = factory.create(ConversionPreset.ORIGINAL_QUALITY, source, WEBM)!!
        assertEquals(video(VideoSettings(VP9, VideoQuality.Crf(16))), s.video)
        assertEquals(audio(AudioSettings(OPUS, bitrateKbps = 256)), s.audio)
    }

    @Test fun source_without_audio_gets_no_audio_plan() =
        assertEquals(AudioPlan.None, factory.create(ConversionPreset.BALANCED, TestMedia.metadata(audio = null), MP4)!!.audio)

    @Test fun codecs_follow_the_container() {
        val webm = factory.create(ConversionPreset.BALANCED, source, WEBM)!!
        assertEquals(VP9, ((webm.video) as VideoPlan.Encode).settings.codec); assertEquals(OPUS, (webm.audio as AudioPlan.Encode).settings.codec)
        val avi = factory.create(ConversionPreset.BALANCED, source, AVI)!!
        assertEquals(MP3, (avi.audio as AudioPlan.Encode).settings.codec)
    }

    @Test fun only_available_encoders_are_used() {
        val f = ConversionPresetFactory(TestMedia.repo(HEVC, AAC))
        assertEquals(HEVC, (f.create(ConversionPreset.BALANCED, source, MP4)!!.video as VideoPlan.Encode).settings.codec)
    }

    @Test fun codecs_without_crf_get_a_bitrate_derived_from_the_source() {
        val f = ConversionPresetFactory(TestMedia.repo(VP8, OPUS))
        val s = f.create(ConversionPreset.BALANCED, source, WEBM)!!
        assertEquals(VideoQuality.Bitrate(3360), (s.video as VideoPlan.Encode).settings.quality)
    }

    @Test fun codecs_without_crf_fall_back_to_2000_kbps_when_source_bitrate_is_unknown() {
        val f = ConversionPresetFactory(TestMedia.repo(VP8, OPUS))
        val s = f.create(ConversionPreset.BALANCED, TestMedia.metadata(TestMedia.video(bitrate = null)), WEBM)!!
        assertEquals(VideoQuality.Bitrate(2000), (s.video as VideoPlan.Encode).settings.quality)
    }

    @Test fun no_video_encoder_for_the_container_means_no_preset() =
        assertNull(ConversionPresetFactory(TestMedia.repo(H264, AAC)).create(ConversionPreset.BALANCED, source, WEBM))

    @Test fun missing_audio_encoder_for_a_source_with_audio_means_no_preset() =
        assertNull(ConversionPresetFactory(TestMedia.repo(H264)).create(ConversionPreset.BALANCED, source, MP4))
}
