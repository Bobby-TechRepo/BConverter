package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ContainerFormat.*
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.ConversionRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFfmpegCommandBuilderTest {
    private val builder = DefaultFfmpegCommandBuilder()
    private val inFile = File("/data/cache/staging/my clip.mp4")
    private val outFile = File("/storage/emulated/0/Movies/out file.mp4")

    private fun video(
        codec: VideoCodec = VideoCodec.H264, quality: VideoQuality = VideoQuality.Crf(23), res: Resolution? = null,
        fps: Double? = null, profile: String? = null, level: String? = null, pix: String? = null,
    ) = VideoPlan.Encode(VideoSettings(codec, quality, res, fps, profile, level, pix))
    private fun audio(codec: AudioCodec = AudioCodec.AAC, br: Int? = 128, rate: Int? = null, ch: Int? = null) =
        AudioPlan.Encode(AudioSettings(codec, br, rate, ch))

    private fun build(
        v: VideoPlan = video(), a: AudioPlan = audio(), c: ContainerFormat = MP4, b: DefaultFfmpegCommandBuilder = builder,
    ): FfmpegCommand = b.buildConversionCommand(
        ConversionRequest("job-1", ResolvedInput(inFile, "my clip.mp4", 1, true), TestMedia.metadata(), outFile, ConversionSettings(c, v, a)),
    )
    private fun FfmpegCommand.opt(name: String) = output.options.value(name)

    // ---- structure ----
    @Test fun input_and_output_paths_are_single_arguments_even_with_spaces() {
        val cmd = build()
        assertEquals(inFile.absolutePath, cmd.inputs.single().path)
        assertEquals(outFile.absolutePath, cmd.output.path)
        val args = cmd.toArguments()
        assertEquals(inFile.absolutePath, args[args.indexOf("-i") + 1])
        assertEquals(outFile.absolutePath, args.last())
    }

    @Test fun global_options_overwrite_but_leave_ffmpeg_stats_enabled_for_the_statistics_callback() {
        val g = build().globalOptions
        assertTrue("-y" in g); assertTrue("-hide_banner" in g)
        assertFalse("-nostats" in g); assertFalse("-progress" in g)
    }

    @Test fun argument_order_is_global_then_input_then_output_options_then_output_path() {
        val args = build().toArguments()
        assertTrue(args.indexOf("-y") < args.indexOf("-i")); assertTrue(args.indexOf("-i") < args.indexOf("-c:v"))
        assertTrue(args.indexOf("-c:v") < args.lastIndex)
    }

    // ---- container ----
    @Test fun explicit_format_is_set_per_container() {
        mapOf(MP4 to "mp4", MKV to "matroska", THREE_GP to "3gp", WEBM to "webm", MOV to "mov", AVI to "avi", MPEG_TS to "mpegts")
            .forEach { (c, f) -> assertEquals(f, build(c = c).opt("-f")) }
    }

    // ---- stream mapping ----
    @Test fun maps_first_video_and_first_audio_stream() =
        assertEquals(listOf("0:v:0", "0:a:0"), build().output.options.values("-map"))

    @Test fun audio_none_maps_only_video_and_disables_audio() {
        val cmd = build(a = AudioPlan.None)
        assertEquals(listOf("0:v:0"), cmd.output.options.values("-map")); assertTrue(cmd.output.options.has("-an"))
        assertFalse(cmd.output.options.has("-c:a"))
    }

    @Test fun subtitles_and_data_streams_are_dropped() { assertTrue(build().output.options.has("-sn")); assertTrue(build().output.options.has("-dn")) }

    // ---- video ----
    @Test fun crf_encoding_uses_the_matching_encoder() {
        val cmd = build(); assertEquals("libx264", cmd.opt("-c:v")); assertEquals("23", cmd.opt("-crf")); assertNull(cmd.opt("-b:v"))
    }

    @Test fun bitrate_encoding_sets_video_bitrate_without_crf() {
        val cmd = build(v = video(quality = VideoQuality.Bitrate(1500)))
        assertEquals("1500k", cmd.opt("-b:v")); assertFalse(cmd.output.options.has("-crf"))
    }

    @Test fun vp9_crf_requires_zero_bitrate() {
        val cmd = build(v = video(VideoCodec.VP9, VideoQuality.Crf(31)), a = audio(AudioCodec.OPUS), c = WEBM)
        assertEquals("libvpx-vp9", cmd.opt("-c:v")); assertEquals("31", cmd.opt("-crf")); assertEquals("0", cmd.opt("-b:v"))
    }

    @Test fun scaling_frame_rate_profile_and_level_are_applied() {
        val cmd = build(v = video(res = Resolution(1280, 720), fps = 24.0, profile = "high", level = "4.1"))
        assertEquals("scale=1280:720", cmd.opt("-vf")); assertEquals("24", cmd.opt("-r"))
        assertEquals("high", cmd.opt("-profile:v")); assertEquals("4.1", cmd.opt("-level"))
    }

    @Test fun fractional_frame_rates_are_not_rounded() = assertEquals("29.97", build(v = video(fps = 29.97)).opt("-r"))

    @Test fun unset_options_are_omitted() {
        val o = build().output.options
        listOf("-vf", "-r", "-profile:v", "-level").forEach { assertFalse(it, o.has(it)) }
    }

    @Test fun pixel_format_defaults_to_yuv420p_and_can_be_overridden() {
        assertEquals("yuv420p", build().opt("-pix_fmt")); assertEquals("yuv422p", build(v = video(pix = "yuv422p")).opt("-pix_fmt"))
    }

    @Test fun faststart_is_set_for_mp4_family_only() {
        listOf(MP4, MOV, THREE_GP).forEach { assertEquals("+faststart", build(c = it).opt("-movflags")) }
        listOf(MKV, AVI).forEach { assertNull(build(c = it, a = audio(AudioCodec.MP3)).opt("-movflags")) }
    }

    @Test fun hevc_in_mp4_family_is_tagged_hvc1() {
        assertEquals("hvc1", build(v = video(VideoCodec.HEVC)).opt("-tag:v"))
        assertNull(build(v = video(VideoCodec.HEVC), c = MKV).opt("-tag:v"))
        assertNull(build().opt("-tag:v"))
    }

    @Test fun video_copy_has_no_encoding_options() {
        val cmd = build(v = VideoPlan.Copy)
        assertEquals("copy", cmd.opt("-c:v"))
        listOf("-crf", "-vf", "-r", "-pix_fmt", "-b:v", "-profile:v").forEach { assertFalse(it, cmd.output.options.has(it)) }
    }

    // ---- audio ----
    @Test fun audio_encoding_options() {
        val cmd = build(a = audio(AudioCodec.AAC, 192, 48000, 2))
        assertEquals("aac", cmd.opt("-c:a")); assertEquals("192k", cmd.opt("-b:a")); assertEquals("48000", cmd.opt("-ar")); assertEquals("2", cmd.opt("-ac"))
    }

    @Test fun audio_defaults_are_omitted_when_unset() {
        val o = build(a = audio(br = null)).output.options
        listOf("-b:a", "-ar", "-ac").forEach { assertFalse(it, o.has(it)) }
    }

    @Test fun audio_copy() = assertEquals("copy", build(a = AudioPlan.Copy).opt("-c:a"))

    @Test fun audio_encoder_names() {
        mapOf(AudioCodec.AAC to "aac", AudioCodec.MP3 to "libmp3lame", AudioCodec.OPUS to "libopus", AudioCodec.VORBIS to "libvorbis",
            AudioCodec.FLAC to "flac", AudioCodec.PCM to "pcm_s16le", AudioCodec.AC3 to "ac3")
            .forEach { (codec, name) -> assertEquals(name, build(a = audio(codec, null), c = MKV).opt("-c:a")) }
    }

    @Test fun video_encoder_names() {
        mapOf(VideoCodec.H264 to "libx264", VideoCodec.HEVC to "libx265", VideoCodec.VP8 to "libvpx", VideoCodec.VP9 to "libvpx-vp9",
            VideoCodec.AV1 to "libsvtav1", VideoCodec.MPEG4 to "mpeg4", VideoCodec.MPEG2 to "mpeg2video")
            .forEach { (codec, name) -> assertEquals(name, build(v = video(codec, VideoQuality.Bitrate(1000)), c = MKV).opt("-c:v")) }
    }

    @Test fun encoder_names_can_be_overridden_for_the_installed_build() {
        val custom = DefaultFfmpegCommandBuilder(EncoderNames.Default.copy(video = EncoderNames.Default.video + (VideoCodec.H264 to "h264_mediacodec")))
        assertEquals("h264_mediacodec", build(b = custom).opt("-c:v"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_codec_without_an_encoder_name_is_a_programming_error() {
        build(b = DefaultFfmpegCommandBuilder(EncoderNames(video = emptyMap(), audio = EncoderNames.Default.audio)))
    }
}
