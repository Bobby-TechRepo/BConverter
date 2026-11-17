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
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real FFmpegKit on a device: a picture-in-picture clip is accepted, laid over the main video, and does not change the output's size or length. */
class PipRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "pip-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun render(name: String, pips: List<PipClip>): MediaMetadata {
        val meta = metadataOf(sample)
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_900)
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(AudioCodec.AAC, 96))
        val project = VideoProject(name, name, Timeline(listOf(clip), pipClips = pips).normalized(), export, mapOf(source to meta))
        val out = File(dir, "$name.mp4").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out))
        val result = try {
            runBlocking { withTimeout(90_000) { FfmpegKitExecutor().execute("$name-${System.nanoTime()}", command.toArguments()) { _, _ -> } } }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("ffmpeg did not finish in 90 s for $name: ${command.toArguments()}")
        }
        assertEquals("render failed for $name: ${command.toArguments()}", FfmpegExecResult.Success, result)
        return metadataOf(out)
    }

    private fun pip(id: String = "p", start: Long = 500, layout: PipLayout = PipLayout(), volume: Float = 1f) =
        PipClip(id, source, maxOf(metadataOf(sample).durationMs, 1_900), 0, 1_000, start, layout, volume)

    private val plain by lazy { render("plain", emptyList()).videoStreams.first().let { it.displayWidth to it.displayHeight } }

    @Test fun a_pip_renders_and_keeps_the_output_size() {
        val s = render("one", listOf(pip())).videoStreams.first(); assertEquals(plain, s.displayWidth to s.displayHeight)
    }
    @Test fun a_semi_transparent_small_pip_in_a_corner_renders() {
        render("corner", listOf(pip(layout = PipLayout(0.15f, 0.85f, 0.2f, 0.5f))))
    }
    @Test fun a_bordered_pip_renders_and_keeps_the_output_size() {
        val s = render("border", listOf(pip(layout = PipLayout(borderWidth = 0.04f, borderRgb = 0xFF8800)))).videoStreams.first(); assertEquals(plain, s.displayWidth to s.displayHeight)
    }
    @Test fun a_pip_with_fades_renders() { render("fades", listOf(pip().copy(fadeInMs = 300, fadeOutMs = 300))) }
    @Test fun a_keyed_and_bordered_pip_renders_and_keeps_the_output_size() {
        val c = pip(layout = PipLayout(borderWidth = 0.03f)).copy(chromaKey = io.bsoft.bconverter.domain.timeline.ChromaKey(0x00FF00, 0.25f, 0.1f))
        val s = render("keyed", listOf(c)).videoStreams.first(); assertEquals(plain, s.displayWidth to s.displayHeight)
    }
    @Test fun a_gliding_pip_renders_and_keeps_the_output_size() {
        val c = pip().copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.25f, 0.75f))
        val s = render("glide", listOf(c)).videoStreams.first(); assertEquals(plain, s.displayWidth to s.displayHeight)
    }
    @Test fun a_gliding_pip_that_changes_size_renders_and_keeps_the_output_size() {
        val c = pip().copy(motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.3f, 0.7f, true, 0.6f))
        val s = render("glide-size", listOf(c)).videoStreams.first(); assertEquals(plain, s.displayWidth to s.displayHeight)
    }
    @Test fun a_fast_and_a_slow_pip_render() {
        render("fast", listOf(pip().copy(speed = 2f))); render("slow", listOf(pip().copy(speed = 0.5f)))
    }
    @Test fun a_rounded_and_a_circular_pip_render_and_keep_the_output_size() {
        val r = render("round", listOf(pip().copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.ROUNDED))).videoStreams.first(); assertEquals(plain, r.displayWidth to r.displayHeight)
        val c = render("circle", listOf(pip().copy(shape = io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE, motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.3f, 0.7f, true, 0.5f)))).videoStreams.first(); assertEquals(plain, c.displayWidth to c.displayHeight)
    }
    @Test fun a_keyframed_pip_with_size_opacity_and_a_shape_renders_and_keeps_the_output_size() {
        val keys = listOf(
            io.bsoft.bconverter.domain.timeline.PipKeyframe(0, 0.2f, 0.2f, 0.2f, 0f), io.bsoft.bconverter.domain.timeline.PipKeyframe(400, 0.7f, 0.4f, 0.5f, 1f), io.bsoft.bconverter.domain.timeline.PipKeyframe(1_000, 0.3f, 0.8f, 0.3f, 0.4f),
        )
        for (shape in io.bsoft.bconverter.domain.timeline.PipShape.values()) {
            val s = render("keys-$shape", listOf(pip().copy(keyframes = keys, shape = shape, fadeInMs = 200))).videoStreams.first(); assertEquals(plain, s.displayWidth to s.displayHeight)
        }
    }
    @Test fun a_muted_pip_renders() { render("muted", listOf(pip(volume = 0f))) }
    @Test fun two_pips_stack() { render("two", listOf(pip("p"), pip("q", start = 1_000, layout = PipLayout(0.25f, 0.75f, 0.3f, 1f)))) }
    @Test fun a_pip_starting_late_and_running_past_the_end_is_cut_with_the_video() {
        val d = render("late", listOf(pip(start = 1_500))).durationMs
        assertEquals("duration $d", 1_900.0, d.toDouble(), 300.0)
    }
}
