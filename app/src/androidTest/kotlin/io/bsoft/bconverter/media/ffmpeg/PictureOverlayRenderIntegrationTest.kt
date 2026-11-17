package io.bsoft.bconverter.media.ffmpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.OverlayLayer
import io.bsoft.bconverter.domain.overlay.OverlayLayerCache
import io.bsoft.bconverter.domain.overlay.OverlayRasterRequest
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.media.overlay.AndroidOverlayRasterizer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real FFmpegKit on a device: a picture overlay is drawn over the video, in the right layer order, and can animate. */
class PictureOverlayRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "picture-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)
    private val cache by lazy { OverlayLayerCache(File(dir, "layers"), AndroidOverlayRasterizer()) }

    @Before fun fonts() { FfmpegFonts.configure(context) }

    private fun solid(name: String, color: Int): File {
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(0f, 0f, 200f, 200f, Paint().apply { this.color = color })
        return File(dir, "$name.png").also { f -> FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun picture(id: String, file: File, scale: Float = 1f, anim: OverlayAnimation? = null) =
        TextOverlay(id, "", 0, 1_800, scale = scale, image = OverlayImage(file.path, 200, 200, heightFraction = 0.5f), animIn = anim)

    private fun metadata(): MediaMetadata = runBlocking {
        (parser.parse((probe.run(sample) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun render(name: String, overlays: List<TextOverlay>): File {
        val meta = metadata()
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_800)
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(AudioCodec.AAC, 96))
        val project = VideoProject(name, name, Timeline(listOf(clip)).normalized(), export, mapOf(source to meta), textOverlays = overlays)
        val canvas = (RenderPlanner.plan(project) as RenderPlanResult.Success).plan.canvas
        val layers = overlays.map { o ->
            val cached = cache.layerFor(OverlayRasterRequest(o, canvas.width, canvas.height, null, 0, imageStamp = o.image!!.path.let { File(it).length() }))
            assertTrue("picture ${o.id} could not be drawn", cached != null)
            OverlayLayer(o, cached!!.file, cached.width, cached.height)
        }
        val out = File(dir, "$name.mp4").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out, overlays = layers))
        val result = try {
            runBlocking { withTimeout(60_000) { FfmpegKitExecutor().execute("$name-${System.nanoTime()}", command.toArguments()) { _, _ -> } } }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("ffmpeg did not finish in 60 s for $name: ${command.toArguments()}")
        }
        assertEquals("render failed: ${command.toArguments()}", FfmpegExecResult.Success, result)
        return out
    }

    private fun centerPixel(video: File, seconds: String, name: String): Int {
        val png = File(dir, "$name.png").apply { delete() }
        assertTrue(ReturnCode.isSuccess(FFmpegKit.execute("-y -ss $seconds -i ${video.absolutePath} -frames:v 1 ${png.absolutePath}").returnCode))
        val b = BitmapFactory.decodeFile(png.absolutePath)
        return b.getPixel(b.width / 2, b.height / 2)
    }

    @Test fun a_picture_covers_the_middle_of_the_frame() {
        val p = centerPixel(render("red", listOf(picture("r", solid("red", Color.RED)))), "0.5", "red")
        assertTrue("expected red, got ${Integer.toHexString(p)}", Color.red(p) > 150 && Color.green(p) < 100 && Color.blue(p) < 100)
    }
    @Test fun the_overlay_later_in_the_list_is_on_top() {
        val red = solid("r2", Color.RED); val blue = solid("b2", Color.BLUE)
        val blueOnTop = centerPixel(render("order1", listOf(picture("r", red), picture("b", blue))), "0.5", "order1")
        val redOnTop = centerPixel(render("order2", listOf(picture("b", blue), picture("r", red))), "0.5", "order2")
        assertTrue("blue on top: ${Integer.toHexString(blueOnTop)}", Color.blue(blueOnTop) > 150 && Color.red(blueOnTop) < 100)
        assertTrue("red on top: ${Integer.toHexString(redOnTop)}", Color.red(redOnTop) > 150 && Color.blue(redOnTop) < 100)
    }
    @Test fun a_fade_in_starts_nearly_invisible_and_ends_fully_visible() {
        val video = render("fade", listOf(picture("r", solid("r3", Color.RED), anim = OverlayAnimation(AnimationKind.FADE, 1_000))))
        val early = centerPixel(video, "0.05", "fade-early"); val late = centerPixel(video, "1.5", "fade-late")
        assertTrue("early ${Integer.toHexString(early)} late ${Integer.toHexString(late)}", abs(Color.green(late) - Color.green(early)) > 40 && Color.red(late) > 150 && Color.green(late) < 100)
    }
}
