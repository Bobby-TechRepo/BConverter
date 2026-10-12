package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
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
import io.bsoft.bconverter.fakes.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Test

/** Metadata: 10 s, video 4.8 Mbps 1920x1080, audio 128 kbps. Output includes a 5 % container overhead. */
class StorageEstimatorTest {
    private val meta = TestMedia.metadata()
    private val input = 7_340_032L
    private fun conv(v: VideoPlan, a: AudioPlan) = ConversionSettings(ContainerFormat.MP4, v, a)
    private fun encode(q: VideoQuality, res: Resolution? = null) = VideoPlan.Encode(VideoSettings(VideoCodec.H264, q, res))
    private fun aac(kbps: Int? = 128) = AudioPlan.Encode(AudioSettings(AudioCodec.AAC, kbps))
    private fun assertAbout(expected: Long, actual: Long) = assertEquals(expected.toDouble(), actual.toDouble(), 2.0)

    @Test fun copying_both_streams_is_about_the_source_bitrate() =
        assertAbout(6_468_000, StorageEstimator.estimateConversion(conv(VideoPlan.Copy, AudioPlan.Copy), meta, input))

    @Test fun a_video_bitrate_sets_the_video_size() =
        assertAbout(2_793_000, StorageEstimator.estimateConversion(conv(encode(VideoQuality.Bitrate(2000)), aac()), meta, input))

    @Test fun crf_without_scaling_assumes_the_source_video_bitrate() =
        assertAbout(6_468_000, StorageEstimator.estimateConversion(conv(encode(VideoQuality.Crf(23)), aac()), meta, input))

    @Test fun crf_with_downscaling_shrinks_with_the_pixel_count() =
        assertAbout(1_743_000, StorageEstimator.estimateConversion(conv(encode(VideoQuality.Crf(23), Resolution(960, 540)), aac()), meta, input))

    @Test fun removing_audio_removes_its_share() =
        assertAbout(6_300_000, StorageEstimator.estimateConversion(conv(VideoPlan.Copy, AudioPlan.None), meta, input))

    @Test fun lossless_audio_without_a_bitrate_is_large() =
        assertAbout(
            ((4_800_000 + 1_411_000) / 8.0 * 10 * 1.05).toLong(),
            StorageEstimator.estimateConversion(conv(VideoPlan.Copy, AudioPlan.Encode(AudioSettings(AudioCodec.FLAC))), meta, input),
        )

    @Test fun an_unknown_duration_falls_back_to_the_input_size() {
        val unknown = MediaMetadata(0, ContainerFormat.MP4, meta.videoStreams, meta.audioStreams)
        assertEquals(input, StorageEstimator.estimateConversion(conv(VideoPlan.Copy, AudioPlan.Copy), unknown, input))
    }

    @Test fun missing_bitrates_are_derived_from_the_file_size() {
        val noRates = TestMedia.metadata(TestMedia.video(bitrate = null), AudioStream(1, "aac", 44100, 2))
        assertAbout(10_500_000, StorageEstimator.estimateConversion(conv(VideoPlan.Copy, AudioPlan.Copy), noRates, 10_000_000))
    }

    // ---- audio extraction ----
    private fun extract(f: AudioFormat, mode: AudioExtractionMode, stream: Int = 0) = AudioExtractionSettings(f, stream, mode)

    @Test fun copying_audio_uses_the_source_audio_bitrate() =
        assertAbout(168_000, StorageEstimator.estimateExtraction(extract(AudioFormat.M4A, AudioExtractionMode.Copy), meta, input))

    @Test fun an_encoded_bitrate_sets_the_size() =
        assertAbout(252_000, StorageEstimator.estimateExtraction(extract(AudioFormat.MP3, AudioExtractionMode.Encode(bitrateKbps = 192)), meta, input))

    @Test fun lossless_extraction_assumes_cd_quality_pcm() =
        assertAbout(1_851_937, StorageEstimator.estimateExtraction(extract(AudioFormat.WAV, AudioExtractionMode.Encode()), meta, input))

    @Test fun the_chosen_track_decides_the_copy_size() {
        val two = MediaMetadata(10_000, ContainerFormat.MKV, audioStreams = listOf(AudioStream(1, "aac", 48000, 2, 128_000), AudioStream(2, "aac", 48000, 2, 256_000)))
        assertAbout(336_000, StorageEstimator.estimateExtraction(extract(AudioFormat.M4A, AudioExtractionMode.Copy, stream = 1), two, input))
    }
}
