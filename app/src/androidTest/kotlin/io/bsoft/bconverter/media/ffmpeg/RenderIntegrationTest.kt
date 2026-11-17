package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.AudioTrack
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Renders real projects with FFmpeg (native mpeg4 + aac encoders) and checks the result with ffprobe. */
class RenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "render").apply { mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun export(audio: AudioSettings? = AudioSettings(AudioCodec.AAC, 96), keepOriginal: Boolean = true) = ExportSettings(
        ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), audio, keepOriginal,
    )

    private fun render(name: String, timeline: Timeline, export: ExportSettings): MediaMetadata {
        val source = MediaSource.FileSource(sample)
        val project = VideoProject(name, name, timeline, export, mapOf(source to metadataOf(sample)))
        val out = File(dir, "$name.mp4").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out))
        val result = runBlocking { FfmpegKitExecutor().execute(name, command.toArguments()) { _, _ -> } }
        assertEquals("render failed for ${command.toArguments()}", FfmpegExecResult.Success, result)
        return metadataOf(out)
    }

    private val source get() = MediaSource.FileSource(sample)
    private val sourceMs get() = maxOf(metadataOf(sample).durationMs, 1_900)

    @Test fun two_trimmed_clips_one_sped_up_with_an_audio_overlay_render_to_the_right_length() {
        val a = VideoClip("a", source, sourceMs, 0, 1_000)                       // 1.0 s
        val b = VideoClip("b", source, sourceMs, 1_000, 1_800, playbackSpeed = 2f) // 0.8 s of source at 2x = 0.4 s
        val overlay = AudioClip("m", source, sourceMs, 0, 1_000, volume = 0.5f, startTimeMs = 500)
        val timeline = Timeline(listOf(a, b), listOf(AudioTrack("t", listOf(overlay)))).normalized()
        val m = render("render-two-clips", timeline, export())
        assertEquals(ContainerFormat.MP4, m.container)
        assertEquals("mpeg4", m.videoStreams.single().codec); assertEquals(160, m.videoStreams.single().width)
        assertEquals("aac", m.audioStreams.single().codec)
        assertTrue("duration ${m.durationMs}", m.durationMs in 1_000..1_900) // expected 1.4 s
    }

    @Test fun slowing_a_clip_down_stretches_it_with_audio_in_sync() {
        val clip = VideoClip("a", source, sourceMs, 0, 500, playbackSpeed = 0.5f) // 0.5 s source at 0.5x = 1.0 s
        val m = render("render-slow", Timeline(listOf(clip)).normalized(), export())
        assertTrue("duration ${m.durationMs}", m.durationMs in 700..1_400); assertEquals(1, m.audioStreams.size)
    }

    @Test fun replacing_the_original_audio_keeps_only_the_added_track() {
        val clip = VideoClip("a", source, sourceMs, 0, 1_000)
        val overlay = AudioClip("m", source, sourceMs, 0, 1_000)
        val timeline = Timeline(listOf(clip), listOf(AudioTrack("t", listOf(overlay)))).normalized()
        val m = render("render-replace", timeline, export(keepOriginal = false))
        assertEquals(1, m.audioStreams.size); assertTrue(m.durationMs in 700..1_400)
    }

    @Test fun a_render_without_audio_settings_has_no_audio_stream() {
        val clip = VideoClip("a", source, sourceMs, 0, 1_000)
        val m = render("render-silent", Timeline(listOf(clip)).normalized(), export(audio = null))
        assertTrue(m.audioStreams.isEmpty()); assertEquals(1, m.videoStreams.size)
    }
}
