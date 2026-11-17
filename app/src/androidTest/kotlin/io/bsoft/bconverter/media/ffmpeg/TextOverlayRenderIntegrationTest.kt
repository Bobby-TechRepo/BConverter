package io.bsoft.bconverter.media.ffmpeg

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
import io.bsoft.bconverter.domain.overlay.OverlayEasing
import io.bsoft.bconverter.domain.overlay.OverlayLayer
import io.bsoft.bconverter.domain.overlay.OverlayLayerCache
import io.bsoft.bconverter.domain.overlay.OverlayOutline
import io.bsoft.bconverter.domain.overlay.OverlayRasterRequest
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.overlay.TextOverlayStyle
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.media.overlay.AndroidOverlayRasterizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real FFmpegKit on a device: text overlays are drawn to pictures, put over the video, animated, and checked frame by frame. */
class TextOverlayRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "overlay-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)
    private val cache by lazy { OverlayLayerCache(File(dir, "layers"), AndroidOverlayRasterizer()) }

    @Before fun fonts() { FfmpegFonts.configure(context) }

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun project(name: String, overlays: List<TextOverlay>, meta: MediaMetadata): VideoProject {
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_800)
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(AudioCodec.AAC, 96))
        return VideoProject(name, name, Timeline(listOf(clip)).normalized(), export, mapOf(source to meta), textOverlays = overlays)
    }

    private fun render(name: String, overlays: List<TextOverlay>): File {
        val meta = metadataOf(sample)
        val p = project(name, overlays, meta)
        val canvas = (RenderPlanner.plan(p) as RenderPlanResult.Success).plan.canvas
        val layers = overlays.map { o ->
            val cached = cache.layerFor(OverlayRasterRequest(o, canvas.width, canvas.height, null, 0))
            assertTrue("overlay ${o.id} could not be drawn", cached != null)
            OverlayLayer(o, cached!!.file, cached.width, cached.height)
        }
        val out = File(dir, "$name.mp4").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(p, RenderOutput(out, overlays = layers))
        val result = try {
            runBlocking { withTimeout(60_000) { FfmpegKitExecutor().execute("$name-${System.nanoTime()}", command.toArguments()) { _, _ -> } } }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("ffmpeg did not finish in 60 s for $name: ${command.toArguments()}")
        }
        assertEquals("render failed: ${command.toArguments()}", FfmpegExecResult.Success, result)
        return out
    }

    private fun frameAt(video: File, seconds: String, name: String): ByteArray {
        val png = File(dir, "$name.png").apply { delete() }
        assertTrue(ReturnCode.isSuccess(FFmpegKit.execute("-y -ss $seconds -i ${video.absolutePath} -frames:v 1 ${png.absolutePath}").returnCode))
        return png.readBytes()
    }

    private val style = TextOverlayStyle(sizeFraction = 0.2f, fontWeight = 700, outline = OverlayOutline())

    /** Mean absolute difference per colour channel (0..255) between two PNG frames of the same size. */
    private fun difference(a: ByteArray, b: ByteArray): Double {
        val x = android.graphics.BitmapFactory.decodeByteArray(a, 0, a.size); val y = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
        assertEquals(x.width, y.width); assertEquals(x.height, y.height)
        val px = IntArray(x.width * x.height).also { x.getPixels(it, 0, x.width, 0, 0, x.width, x.height) }
        val py = IntArray(y.width * y.height).also { y.getPixels(it, 0, y.width, 0, 0, y.width, y.height) }
        var sum = 0L
        for (i in px.indices) for (shift in intArrayOf(0, 8, 16)) sum += kotlin.math.abs(((px[i] shr shift) and 0xFF) - ((py[i] shr shift) and 0xFF))
        return sum.toDouble() / (px.size * 3)
    }

    @Test fun text_changes_the_picture_while_it_is_shown_and_only_then() {
        val plain = render("plain", emptyList())
        val text = render("text", listOf(TextOverlay("t", "OVERLAY", 200, 900, style = style)))
        // Two separate encodes never match bit for bit, so compare how different the frames are: the picture with text must differ
        // much more during the overlay than the encoder noise does after it.
        val during = difference(frameAt(plain, "0.5", "p1"), frameAt(text, "0.5", "t1"))
        val after = difference(frameAt(plain, "1.5", "p2"), frameAt(text, "1.5", "t2"))
        assertTrue("during=$during after=$after", during > 0.5 && after < during / 3)
    }

    /** One test per kind, each with its own 60 s limit, so a hang is pinned on one kind instead of freezing the whole run. */
    private fun animationRenders(kind: AnimationKind) {
        val o = TextOverlay("t", "Hi", 100, 1_500, style = style, animIn = OverlayAnimation(kind, 400, OverlayEasing.EASE_IN_OUT), animOut = OverlayAnimation(kind, 400, OverlayEasing.EASE_OUT))
        val out = render("anim-${kind.id}", listOf(o))
        assertTrue(kind.id, metadataOf(out).videoStreams.isNotEmpty())
    }

    @Test(timeout = 90_000) fun animation_fade_renders() = animationRenders(AnimationKind.FADE)
    @Test(timeout = 90_000) fun animation_slide_left_renders() = animationRenders(AnimationKind.SLIDE_LEFT)
    @Test(timeout = 90_000) fun animation_slide_right_renders() = animationRenders(AnimationKind.SLIDE_RIGHT)
    @Test(timeout = 90_000) fun animation_slide_up_renders() = animationRenders(AnimationKind.SLIDE_UP)
    @Test(timeout = 90_000) fun animation_slide_down_renders() = animationRenders(AnimationKind.SLIDE_DOWN)
    @Test(timeout = 90_000) fun animation_scale_renders() = animationRenders(AnimationKind.SCALE)

    @Test(timeout = 90_000) fun a_keyframed_text_with_position_zoom_and_opacity_renders() {
        val keys = listOf(
            io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.2f, 0.2f, 0.5f, 0f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(500, 0.7f, 0.5f, 1.5f, 1f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(1_200, 0.3f, 0.8f, 1f, 0.4f),
        )
        val out = render("keys", listOf(TextOverlay("k", "KEYS", 200, 1_600, style = style, keyframes = keys)))
        val m = metadataOf(out); assertTrue(m.videoStreams.isNotEmpty()); assertTrue(m.durationMs in 1_500..2_300)
        // Early on the text is nearly invisible (opacity 0); in the middle it is plainly there.
        val plain = render("keys-plain", emptyList())
        val early = difference(frameAt(plain, "0.25", "kp1"), frameAt(out, "0.25", "k1")); val middle = difference(frameAt(plain, "0.7", "kp2"), frameAt(out, "0.7", "k2"))
        assertTrue("early=$early middle=$middle", middle > early)
    }
    @Test(timeout = 90_000) fun a_keyframed_turn_renders_and_the_text_is_turned_by_the_end() {
        fun keys(end: Float) = listOf(
            io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.5f, 0.5f, rotationDeg = 0f),
            io.bsoft.bconverter.domain.overlay.OverlayKeyframe(1_000, 0.5f, 0.5f, rotationDeg = end),
        )
        val turning = render("turn", listOf(TextOverlay("k", "TURN", 200, 1_600, style = style, keyframes = keys(90f))))
        val still = render("turn-still", listOf(TextOverlay("k", "TURN", 200, 1_600, style = style, keyframes = keys(0f))))
        val m = metadataOf(turning); assertTrue(m.videoStreams.isNotEmpty()); assertTrue(m.durationMs in 1_500..2_300)
        // At the start both look the same; by the end one is turned a quarter circle, which changes many pixels.
        val start = difference(frameAt(turning, "0.25", "tn1"), frameAt(still, "0.25", "ts1"))
        val end = difference(frameAt(turning, "1.5", "tn2"), frameAt(still, "1.5", "ts2"))
        assertTrue("start=$start end=$end", end > 0.5 && end > start * 3)
    }
    @Test fun several_overlays_with_rotation_and_background_render_together() {
        val a = TextOverlay("a", "One", 0, 1_800, centerX = 0.3f, centerY = 0.3f, rotationDeg = 15f, style = style)
        val b = TextOverlay("b", "Two\nlines", 300, 1_500, centerX = 0.7f, centerY = 0.7f, style = style.copy(background = io.bsoft.bconverter.domain.overlay.OverlayBackground()), widthFraction = 0.4f)
        val out = render("several", listOf(a, b))
        val m = metadataOf(out)
        assertTrue(m.videoStreams.isNotEmpty()); assertTrue(m.durationMs in 1_500..2_300)
    }
}
