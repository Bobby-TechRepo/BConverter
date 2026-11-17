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
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropRect
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real FFmpegKit on a device: crop, rotation and mirroring are accepted and give the output the right shape. */
class ClipTransformRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "transform-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    /** Renders one clip with [t] and returns the output's picture size (width to height). */
    private fun render(name: String, t: ClipTransform, reversed: Boolean = false, freezeMs: Long = 0): Pair<Int, Int> {
        val meta = metadataOf(sample)
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_000, transform = t, reversed = reversed, freezeMs = freezeMs)
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(AudioCodec.AAC, 96))
        val project = VideoProject(name, name, Timeline(listOf(clip)).normalized(), export, mapOf(source to meta))
        val out = File(dir, "$name.mp4").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out))
        val result = try {
            runBlocking { withTimeout(60_000) { FfmpegKitExecutor().execute("$name-${System.nanoTime()}", command.toArguments()) { _, _ -> } } }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("ffmpeg did not finish in 60 s for $name: ${command.toArguments()}")
        }
        assertEquals("render failed for $name: ${command.toArguments()}", FfmpegExecResult.Success, result)
        val s = metadataOf(out).videoStreams.first()
        return s.displayWidth to s.displayHeight
    }

    private val shot get() = metadataOf(sample).videoStreams.first().let { it.displayWidth to it.displayHeight }
    private fun even(v: Int) = v - v % 2

    @Test fun rotating_right_turns_the_output_on_its_side() {
        val (w, h) = shot; assertEquals(even(h) to even(w), render("rot90", ClipTransform(rotationDegrees = 90)))
    }
    @Test fun rotating_left_turns_the_output_on_its_side() {
        val (w, h) = shot; assertEquals(even(h) to even(w), render("rot270", ClipTransform(rotationDegrees = 270)))
    }
    @Test fun a_half_turn_keeps_the_shape() {
        val (w, h) = shot; assertEquals(even(w) to even(h), render("rot180", ClipTransform(rotationDegrees = 180)))
    }
    @Test fun mirroring_keeps_the_shape() {
        val (w, h) = shot
        assertEquals(even(w) to even(h), render("flipH", ClipTransform(flipHorizontal = true)))
        assertEquals(even(w) to even(h), render("flipV", ClipTransform(flipVertical = true)))
    }
    @Test fun a_centred_crop_halves_each_side() {
        val (w, h) = shot
        val (cw, ch) = render("crop", ClipTransform(crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f)))
        assertEquals(even(ClipTransform.croppedSide(w, 0.25f, 0.75f)), cw); assertEquals(even(ClipTransform.croppedSide(h, 0.25f, 0.75f)), ch)
    }
    @Test fun a_reversed_clip_renders_at_its_own_size() {
        val (w, h) = shot; assertEquals(even(w) to even(h), render("reversed", ClipTransform.None, reversed = true))
    }
    @Test fun a_reversed_clip_can_also_be_turned() {
        val (w, h) = shot; assertEquals(even(h) to even(w), render("reversedTurned", ClipTransform(rotationDegrees = 90), reversed = true))
    }
    @Test fun a_freeze_frame_renders_as_a_picture_of_the_right_shape_and_length() {
        val (w, h) = shot
        assertEquals(even(w) to even(h), render("freeze", ClipTransform.None, freezeMs = 2_000))
        val d = metadataOf(File(dir, "freeze.mp4")).durationMs
        assertEquals("freeze length ${d} ms", 2_000.0, d.toDouble(), 250.0)
    }
    @Test fun crop_turn_and_mirror_together() {
        val t = ClipTransform(CropRect(0f, 0f, 0.5f, 1f), 90, flipHorizontal = true, flipVertical = true)
        val (w, h) = shot
        assertEquals(even(h) to even(ClipTransform.croppedSide(w, 0f, 0.5f)), render("all", t))
    }
}
