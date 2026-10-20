package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec.*
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec.*
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.fakes.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportSettingsFactoryTest {
    private val all = ExportSettingsFactory(TestMedia.allEncoders())
    private val hd = Resolution(1920, 1080)

    @Test fun balanced_uses_crf_23_and_128k_audio() = assertEquals(
        ExportSettings(MP4, VideoSettings(H264, VideoQuality.Crf(23)), AudioSettings(AAC, 128), keepOriginalAudio = true),
        all.create(ExportPreset.BALANCED, MP4, hd),
    )

    @Test fun high_quality_uses_crf_18_and_192k_audio() {
        val s = all.create(ExportPreset.HIGH_QUALITY, MP4, hd)!!
        assertEquals(VideoQuality.Crf(18), s.video.quality); assertEquals(192, s.audio!!.bitrateKbps)
    }

    @Test fun small_file_uses_crf_28_96k_audio_and_caps_the_canvas_at_720p() {
        val s = all.create(ExportPreset.SMALL_FILE, MP4, hd)!!
        assertEquals(VideoQuality.Crf(28), s.video.quality); assertEquals(96, s.audio!!.bitrateKbps)
        assertEquals(Resolution(1280, 720), s.video.resolution)
    }

    @Test fun small_file_never_upscales_or_guesses_when_the_size_is_unknown() {
        assertNull(all.create(ExportPreset.SMALL_FILE, MP4, Resolution(640, 360))!!.video.resolution)
        assertNull(all.create(ExportPreset.SMALL_FILE, MP4, null)!!.video.resolution)
    }

    @Test fun balanced_and_high_keep_the_canvas_of_the_first_clip() = assertNull(all.create(ExportPreset.BALANCED, MP4, hd)!!.video.resolution)

    @Test fun codecs_follow_the_container() {
        val s = all.create(ExportPreset.BALANCED, WEBM, hd)!!
        assertEquals(VP9, s.video.codec); assertEquals(OPUS, s.audio!!.codec)
    }

    @Test fun only_available_encoders_are_used() =
        assertEquals(HEVC, ExportSettingsFactory(TestMedia.repo(HEVC, AAC)).create(ExportPreset.BALANCED, MP4, hd)!!.video.codec)

    // ---- bitrate-only encoders: pixels x 30 fps x bits-per-pixel ----
    private val bitrateOnly = ExportSettingsFactory(TestMedia.repoBitrateOnly(H264, AAC))

    @Test fun bitrate_only_encoders_get_a_bitrate_from_the_canvas_size() {
        assertEquals(VideoQuality.Bitrate(6221), bitrateOnly.create(ExportPreset.BALANCED, MP4, hd)!!.video.quality)
        assertEquals(VideoQuality.Bitrate(9331), bitrateOnly.create(ExportPreset.HIGH_QUALITY, MP4, hd)!!.video.quality)
    }

    @Test fun the_bitrate_of_a_small_file_is_based_on_the_capped_canvas() =
        assertEquals(VideoQuality.Bitrate(1659), bitrateOnly.create(ExportPreset.SMALL_FILE, MP4, hd)!!.video.quality)

    @Test fun an_unknown_canvas_is_assumed_to_be_full_hd_for_the_bitrate() =
        assertEquals(VideoQuality.Bitrate(6221), bitrateOnly.create(ExportPreset.BALANCED, MP4, null)!!.video.quality)

    @Test fun codecs_without_crf_support_use_a_bitrate_too() =
        assertEquals(VideoQuality.Bitrate(6221), ExportSettingsFactory(TestMedia.repo(VP8, OPUS)).create(ExportPreset.BALANCED, WEBM, hd)!!.video.quality)

    // ---- limits ----
    @Test fun without_an_audio_encoder_the_export_is_silent() = assertNull(ExportSettingsFactory(TestMedia.repo(H264)).create(ExportPreset.BALANCED, MP4, hd)!!.audio)

    @Test fun without_a_video_encoder_there_is_no_export() = assertNull(ExportSettingsFactory(TestMedia.repo(AAC)).create(ExportPreset.BALANCED, MP4, hd))
}
