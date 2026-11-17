package io.bsoft.bconverter.media.ffmpeg

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.FilterParam
import io.bsoft.bconverter.domain.filter.FilterPresets
import io.bsoft.bconverter.domain.filter.ToneMode
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
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FFmpegKit on a device: every preset and every slider extreme is accepted by this build's filters, and filters change the picture. */
class ClipFilterRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "filter-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)

    private fun metadata(): MediaMetadata = runBlocking {
        (parser.parse((probe.run(sample) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun render(name: String, filter: ClipFilter): File {
        val meta = metadata()
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_000, filter = filter)
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
        return out
    }

    private fun pixels(video: File, name: String): IntArray {
        val png = File(dir, "$name.png").apply { delete() }
        assertTrue(ReturnCode.isSuccess(FFmpegKit.execute("-y -ss 0.4 -i ${video.absolutePath} -frames:v 1 -vf scale=64:36 ${png.absolutePath}").returnCode))
        val b = BitmapFactory.decodeFile(png.absolutePath)
        return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    }

    private fun meanDifference(a: IntArray, b: IntArray): Double =
        a.indices.sumOf { i -> (0..2).sumOf { c -> abs(((a[i] shr (c * 8)) and 255) - ((b[i] shr (c * 8)) and 255)).toDouble() } } / (a.size * 3.0)

    // One test per filter: if the device's FFmpeg ever crashes on one, the report names it instead of just "process crashed".
    private fun preset(id: String) { render("preset-$id", FilterPresets.byId(id)!!.filter) }
    private fun slider(key: String, max: Boolean) { val p = FilterParam.entries.first { it.key == key }; render("${if (max) "max" else "min"}-$key", p.set(ClipFilter.None, if (max) p.max else p.min)) }

    @Test fun preset_original_renders() = preset("original")
    @Test fun preset_vivid_renders() = preset("vivid")
    @Test fun preset_warm_renders() = preset("warm")
    @Test fun preset_cool_renders() = preset("cool")
    @Test fun preset_vintage_renders() = preset("vintage")
    @Test fun preset_noir_renders() = preset("noir")
    @Test fun preset_sepia_renders() = preset("sepia")
    @Test fun preset_matte_renders() = preset("matte")
    @Test fun preset_cinematic_renders() = preset("cinematic")
    @Test fun preset_dramatic_renders() = preset("dramatic")
    @Test fun preset_soft_renders() = preset("soft")
    @Test fun preset_sharp_renders() = preset("sharp")
    @Test fun preset_sunset_renders() = preset("sunset")
    @Test fun preset_moody_renders() = preset("moody")
    @Test fun preset_negative_renders() = preset("negative")

    @Test fun slider_brightness_max_renders() = slider("brightness", true)
    @Test fun slider_brightness_min_renders() = slider("brightness", false)
    @Test fun slider_contrast_max_renders() = slider("contrast", true)
    @Test fun slider_contrast_min_renders() = slider("contrast", false)
    @Test fun slider_saturation_max_renders() = slider("saturation", true)
    @Test fun slider_saturation_min_renders() = slider("saturation", false)
    @Test fun slider_exposure_max_renders() = slider("exposure", true)
    @Test fun slider_exposure_min_renders() = slider("exposure", false)
    @Test fun slider_temperature_max_renders() = slider("temperature", true)
    @Test fun slider_temperature_min_renders() = slider("temperature", false)
    @Test fun slider_tint_max_renders() = slider("tint", true)
    @Test fun slider_tint_min_renders() = slider("tint", false)
    @Test fun slider_hue_max_renders() = slider("hue", true)
    @Test fun slider_hue_min_renders() = slider("hue", false)
    @Test fun slider_sharpen_max_renders() = slider("sharpen", true)
    @Test fun slider_blur_max_renders() = slider("blur", true)
    @Test fun slider_vignette_max_renders() = slider("vignette", true)
    @Test fun slider_grain_max_renders() = slider("grain", true)
    @Test fun slider_highlights_max_renders() = slider("highlights", true)
    @Test fun slider_highlights_min_renders() = slider("highlights", false)
    @Test fun slider_shadows_max_renders() = slider("shadows", true)
    @Test fun slider_shadows_min_renders() = slider("shadows", false)
    @Test fun slider_curve_max_renders() = slider("curve", true)
    @Test fun slider_curve_min_renders() = slider("curve", false)
    @Test fun slider_fade_max_renders() = slider("fade", true)

    @Test fun sepia_tone_renders() { render("sepia", ClipFilter(tone = ToneMode.SEPIA)) }
    @Test fun black_and_white_tone_renders() { render("bw", ClipFilter(tone = ToneMode.BLACK_WHITE)) }
    @Test fun invert_renders() { render("invert", ClipFilter(invert = true)) }

    @Test fun the_identity_renders_the_same_twice() {
        assertTrue(meanDifference(pixels(render("plain", ClipFilter.None), "plain"), pixels(render("plain2", ClipFilter.None), "plain2")) < 2.0)
    }
    @Test fun invert_changes_the_picture() {
        val d = meanDifference(pixels(render("plain3", ClipFilter.None), "plain3"), pixels(render("inverted", ClipFilter(invert = true)), "inverted"))
        assertTrue("mean difference $d", d > 20.0)
    }
    @Test fun brightness_changes_the_picture() {
        val d = meanDifference(pixels(render("plain4", ClipFilter.None), "plain4"), pixels(render("brighter", ClipFilter(brightness = 0.8f)), "brighter"))
        assertTrue("mean difference $d", d > 8.0)
    }
}
