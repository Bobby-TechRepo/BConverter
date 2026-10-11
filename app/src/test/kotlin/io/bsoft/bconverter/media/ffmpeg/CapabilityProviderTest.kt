package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.fakes.FakeFfmpegListRunner
import io.bsoft.bconverter.fakes.FfmpegListings.audio
import io.bsoft.bconverter.fakes.FfmpegListings.of
import io.bsoft.bconverter.fakes.FfmpegListings.video
import io.bsoft.bconverter.media.device.CodecInfo
import io.bsoft.bconverter.media.device.CodecInfoProvider
import io.bsoft.bconverter.media.device.MediaCodecCapabilitySource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityProviderTest {
    private fun device(vararg infos: CodecInfo) =
        MediaCodecCapabilitySource(object : CodecInfoProvider { override fun codecs() = infos.toList() })
    private val avcEncoder = CodecInfo("c2.avc.encoder", listOf("video/avc"), isEncoder = true)

    private fun provider(runner: FakeFfmpegListRunner, device: MediaCodecCapabilitySource = device()) = CapabilityProvider(runner, device)
    private val software = FakeFfmpegListRunner(
        encoders = of(video("libx264"), video("libvpx-vp9"), audio("aac"), audio("libopus")), decoders = of(video("h264")),
    )

    @Test fun repository_reflects_the_installed_encoders() = runTest {
        val repo = provider(software).repository()
        assertEquals(listOf(VideoCodec.H264, VideoCodec.VP9), repo.getVideoCodecs(ContainerFormat.MP4))
        assertTrue(ContainerFormat.WEBM in repo.getContainers())
    }

    @Test fun crf_support_comes_from_the_installed_encoder() = runTest {
        val repo = provider(software).repository()
        assertTrue(repo.supportsCrf(VideoCodec.H264)); assertTrue(repo.supportsCrf(VideoCodec.VP9))
    }

    @Test fun encoder_names_reflect_the_installed_encoders() = runTest {
        val runner = FakeFfmpegListRunner(encoders = of(video("libkvazaar")), decoders = of())
        assertEquals("libkvazaar", provider(runner).encoderNames().video[VideoCodec.HEVC])
    }

    @Test fun mediacodec_wrappers_need_a_matching_device_encoder() = runTest {
        val runner = FakeFfmpegListRunner(encoders = of(video("h264_mediacodec")), decoders = of())
        assertFalse(provider(runner, device()).repository().getVideoCodecs(ContainerFormat.MP4).contains(VideoCodec.H264))
        assertTrue(provider(runner, device(avcEncoder)).repository().getVideoCodecs(ContainerFormat.MP4).contains(VideoCodec.H264))
    }

    @Test fun listings_are_loaded_once_and_cached() = runTest {
        val p = provider(software)
        p.repository(); p.encoderNames(); p.repository()
        assertEquals(1, software.encoderCalls); assertEquals(1, software.decoderCalls)
    }

    @Test fun unavailable_ffmpeg_yields_an_empty_repository_and_default_names() = runTest {
        val p = provider(FakeFfmpegListRunner(encoders = null, decoders = null))
        assertTrue(p.repository().getContainers().isEmpty())
        assertEquals(EncoderNames.Default, p.encoderNames())
    }
}
