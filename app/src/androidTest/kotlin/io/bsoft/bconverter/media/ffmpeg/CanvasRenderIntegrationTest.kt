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
import io.bsoft.bconverter.domain.canvas.CanvasAspect
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real FFmpegKit on a device: each canvas shape, fit and background renders, and the output has the shape asked for. */
class CanvasRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "canvas-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun render(name: String, canvas: CanvasSettings): MediaMetadata {
        val meta = metadataOf(sample)
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_900)
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(AudioCodec.AAC, 96))
        val project = VideoProject(name, name, Timeline(listOf(clip)).normalized(), export, mapOf(source to meta), canvas = canvas)
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

    private fun size(m: MediaMetadata) = m.videoStreams.first().let { it.displayWidth to it.displayHeight }
    private val short get() = metadataOf(sample).videoStreams.first().let { minOf(it.displayWidth, it.displayHeight) }.let { it - it % 2 }

    @Test fun a_tall_canvas_with_a_colour_renders_with_the_asked_shape() {
        val (w, h) = size(render("tall-colour", CanvasSettings(CanvasAspect.TALL, background = CanvasBackground.Color(0x336699))))
        assertEquals(short, w); assertEquals(CanvasSettings(CanvasAspect.TALL).sizeFor(w, h).second, h)
    }
    @Test fun a_tall_canvas_with_a_blurred_background_renders() {
        val (w, h) = size(render("tall-blur", CanvasSettings(CanvasAspect.TALL, background = CanvasBackground.Blur)))
        assertEquals(short, w); assertEquals(true, h > w)
    }
    @Test fun a_square_canvas_with_fill_renders() {
        val (w, h) = size(render("square-fill", CanvasSettings(CanvasAspect.SQUARE, fit = CanvasFit.FILL))); assertEquals(w, h)
    }
    @Test fun the_default_canvas_keeps_the_first_clips_shape() {
        val s = metadataOf(sample).videoStreams.first(); val (w, h) = size(render("auto", CanvasSettings()))
        assertEquals(s.displayWidth - s.displayWidth % 2, w); assertEquals(s.displayHeight - s.displayHeight % 2, h)
    }
    @Test fun a_blur_background_on_a_picture_that_already_fits_renders() { render("auto-blur", CanvasSettings(background = CanvasBackground.Blur)) }
}
