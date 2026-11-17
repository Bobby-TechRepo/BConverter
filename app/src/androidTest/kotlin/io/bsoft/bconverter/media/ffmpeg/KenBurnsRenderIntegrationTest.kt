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
import io.bsoft.bconverter.domain.transform.KenBurns
import io.bsoft.bconverter.domain.transform.KenBurnsMode
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real FFmpegKit on a device: a slow zoom or pan is accepted and leaves the output the size of the picture. */
class KenBurnsRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "kenburns-render").apply { deleteRecursively(); mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    /** Renders one clip with the motion [k] and returns the output's picture size (width to height). */
    private fun render(name: String, k: KenBurns, reversed: Boolean = false): Pair<Int, Int> {
        val meta = metadataOf(sample)
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_000, kenBurns = k, reversed = reversed)
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(AudioCodec.AAC, 96))
        val project = VideoProject(name, name, Timeline(listOf(clip)).normalized(), export, mapOf(source to meta))
        val out = File(dir, "$name.mp4").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out))
        val args = command.toArguments()
        val latch = java.util.concurrent.CountDownLatch(1)
        var code: com.arthenica.ffmpegkit.ReturnCode? = null
        val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(args.toTypedArray(), { finished -> code = finished.returnCode; latch.countDown() })
        if (!latch.await(45, java.util.concurrent.TimeUnit.SECONDS)) {
            val logs = session.allLogsAsString.takeLast(3_000)
            Thread { com.arthenica.ffmpegkit.FFmpegKit.cancel(session.sessionId) }.apply { isDaemon = true }.start()
            throw AssertionError("HANG (45 s) for $name\nargs: $args\nlast log:\n$logs")
        }
        assertEquals("render failed for $name: $args\n${session.allLogsAsString.takeLast(2_000)}", true, com.arthenica.ffmpegkit.ReturnCode.isSuccess(code))
        val s = metadataOf(out).videoStreams.first()
        return s.displayWidth to s.displayHeight
    }

    private val shot get() = metadataOf(sample).videoStreams.first().let { it.displayWidth to it.displayHeight }
    private fun even(v: Int) = v - v % 2

    private fun keepsSize(m: KenBurnsMode) { val (w, h) = shot; assertEquals(m.name, even(w) to even(h), render("kb_${m.name}", KenBurns(m, 1.5f))) }
    // One test per mode so a hang names the mode; each gives up after 45 s with the FFmpeg log instead of running forever.
    @Test fun none_renders() = keepsSize(KenBurnsMode.NONE)
    @Test fun zoom_in_renders() = keepsSize(KenBurnsMode.ZOOM_IN)
    @Test fun zoom_out_renders() = keepsSize(KenBurnsMode.ZOOM_OUT)
    @Test fun pan_left_renders() = keepsSize(KenBurnsMode.PAN_LEFT)
    @Test fun pan_up_renders() = keepsSize(KenBurnsMode.PAN_UP)
    @Test fun a_reversed_clip_with_motion_renders() {
        val (w, h) = shot; assertEquals(even(w) to even(h), render("kb_rev", KenBurns(KenBurnsMode.ZOOM_IN, 1.5f), reversed = true))
    }
}
