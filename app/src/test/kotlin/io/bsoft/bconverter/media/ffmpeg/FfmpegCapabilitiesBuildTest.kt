package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.fakes.FfmpegListings.audio
import io.bsoft.bconverter.fakes.FfmpegListings.experimentalAudio
import io.bsoft.bconverter.fakes.FfmpegListings.of
import io.bsoft.bconverter.fakes.FfmpegListings.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCapabilitiesBuildTest {
    private val none = of()
    private fun avail(enc: String, device: Set<Codec>? = null) = FfmpegCapabilities.fromListings(enc, none, device)
    private fun List<CodecAvailability>.of(c: Codec) = singleOrNull { it.codec == c }
    private fun names(enc: String, device: Set<Codec>? = null) = FfmpegCapabilities.encoderNames(enc, device)

    // ---- rate control ----
    @Test fun software_encoders_with_crf_are_flagged() {
        listOf("libx264" to VideoCodec.H264, "libx265" to VideoCodec.HEVC, "libvpx-vp9" to VideoCodec.VP9, "libsvtav1" to VideoCodec.AV1)
            .forEach { (name, codec) -> assertTrue(name, avail(of(video(name))).of(codec)!!.supportsCrf) }
    }

    @Test fun mediacodec_kvazaar_and_native_encoders_are_bitrate_only() {
        listOf("h264_mediacodec" to VideoCodec.H264, "libkvazaar" to VideoCodec.HEVC, "mpeg4" to VideoCodec.MPEG4)
            .forEach { (name, codec) -> assertFalse(name, avail(of(video(name))).of(codec)!!.supportsCrf) }
    }

    @Test fun a_crf_encoder_alongside_a_bitrate_only_one_gives_crf_support() =
        assertTrue(avail(of(video("h264_mediacodec"), video("libx264"))).of(VideoCodec.H264)!!.supportsCrf)

    @Test fun kvazaar_and_shine_are_recognised_encoders() {
        val list = avail(of(video("libkvazaar"), audio("libshine")))
        assertTrue(list.of(VideoCodec.HEVC)!!.canEncode); assertTrue(list.of(AudioCodec.MP3)!!.canEncode)
    }

    // ---- device cross-check for MediaCodec wrappers ----
    @Test fun mediacodec_encoder_is_ignored_when_the_device_has_no_such_encoder() =
        assertNull(avail(of(video("h264_mediacodec")), device = emptySet()).of(VideoCodec.H264))

    @Test fun mediacodec_encoder_counts_when_the_device_has_the_encoder() =
        assertTrue(avail(of(video("h264_mediacodec")), device = setOf(VideoCodec.H264)).of(VideoCodec.H264)!!.canEncode)

    @Test fun software_encoders_ignore_the_device_filter() =
        assertTrue(avail(of(video("libx264")), device = emptySet()).of(VideoCodec.H264)!!.canEncode)

    // ---- encoder names for the installed build ----
    @Test fun a_crf_capable_encoder_is_preferred_over_mediacodec() =
        assertEquals("libx264", names(of(video("h264_mediacodec"), video("libx264"))).video[VideoCodec.H264])

    @Test fun the_only_available_encoder_is_used() {
        assertEquals("h264_mediacodec", names(of(video("h264_mediacodec"))).video[VideoCodec.H264])
        assertEquals("libkvazaar", names(of(video("libkvazaar"))).video[VideoCodec.HEVC])
        assertEquals("libshine", names(of(audio("libshine"))).audio[AudioCodec.MP3])
    }

    @Test fun lame_is_preferred_over_shine() =
        assertEquals("libmp3lame", names(of(audio("libshine"), audio("libmp3lame"))).audio[AudioCodec.MP3])

    @Test fun codecs_without_an_installed_encoder_keep_the_default_name() =
        assertEquals(EncoderNames.Default.video[VideoCodec.AV1], names(of(video("libx264"))).video[VideoCodec.AV1])

    @Test fun experimental_encoders_are_not_chosen() =
        assertEquals(EncoderNames.Default.audio[AudioCodec.OPUS], names(of(experimentalAudio("opus"))).audio[AudioCodec.OPUS])

    @Test fun unusable_mediacodec_wrappers_are_not_chosen() =
        assertEquals(EncoderNames.Default.video[VideoCodec.H264], names(of(video("h264_mediacodec")), device = emptySet()).video[VideoCodec.H264])
}
