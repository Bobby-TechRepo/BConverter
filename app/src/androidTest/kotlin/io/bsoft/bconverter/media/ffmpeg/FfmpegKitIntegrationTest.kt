package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.media.api.ConversionRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the real FFmpegKit (arm64-v8a device or emulator required). Fixtures are generated with FFmpeg's
 * lavfi test sources and the native mpeg4/aac encoders, so no binary assets are checked in.
 */
class FfmpegKitIntegrationTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "it").apply { mkdirs() }
    private val sample = File(dir, "sample.mp4")
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()

    @Before fun makeFixture() {
        sample.delete()
        val session = FFmpegKit.executeWithArguments(arrayOf(
            "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=15", "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "mpeg4", "-c:a", "aac", "-shortest", sample.absolutePath,
        ))
        assertTrue("fixture generation failed: ${session.output}", ReturnCode.isSuccess(session.returnCode))
    }

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        val run = probe.run(file) as FfprobeRunResult.Success
        (parser.parse(run.json) as FfprobeParseResult.Parsed).metadata
    }

    @Test fun ffmpeg_lists_its_native_encoders() = runBlocking {
        val encoders = FfmpegKitListRunner(Dispatchers.IO).listEncoders()
        assertTrue(encoders != null && FfmpegCodecListParser.parse(encoders).any { it.name == "aac" } &&
            FfmpegCodecListParser.parse(encoders).any { it.name == "mpeg4" })
    }

    @Test fun probe_reads_the_generated_file() {
        val m = metadataOf(sample)
        assertEquals(ContainerFormat.MP4, m.container)
        assertEquals("mpeg4", m.videoStreams.single().codec); assertEquals("aac", m.audioStreams.single().codec)
        assertEquals(320, m.videoStreams.single().width)
        assertTrue("duration ${m.durationMs}", m.durationMs in 1500..2600)
    }

    @Test fun mp4_to_mkv_by_stream_copy_keeps_codecs_and_duration() {
        val out = File(dir, "copy.mkv").apply { delete() }
        val meta = metadataOf(sample)
        val request = ConversionRequest(
            "it-1", ResolvedInput(sample, "sample.mp4", sample.length(), false), meta, out,
            ConversionSettings(ContainerFormat.MKV, VideoPlan.Copy, AudioPlan.Copy),
        )
        val result = runBlocking {
            FfmpegKitExecutor().execute("it-1", DefaultFfmpegCommandBuilder().buildConversionCommand(request).toArguments()) { _, _ -> }
        }
        assertEquals(FfmpegExecResult.Success, result)
        val m = metadataOf(out)
        assertEquals(ContainerFormat.MKV, m.container)
        assertEquals("mpeg4", m.videoStreams.single().codec); assertEquals("aac", m.audioStreams.single().codec)
        assertTrue(kotlin.math.abs(m.durationMs - meta.durationMs) < 400)
    }
}
