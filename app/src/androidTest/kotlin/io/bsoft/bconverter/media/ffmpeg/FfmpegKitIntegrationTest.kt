package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.IntegrationFixtures
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the real FFmpegKit (arm64-v8a device or emulator required). Fixtures are generated with FFmpeg's
 * lavfi test sources and the native mpeg4/aac encoders, so no binary assets are checked in.
 */
class FfmpegKitIntegrationTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "it").apply { mkdirs() }
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(InstrumentationRegistry.getInstrumentation().targetContext) }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()

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
        assertEquals(160, m.videoStreams.single().width)
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

    // ---- audio extraction ----
    private fun extract(format: AudioFormat, mode: AudioExtractionMode): File {
        val out = File(dir, "audio.${format.extension}").apply { delete() }
        val request = AudioExtractionRequest(
            "it-audio", ResolvedInput(sample, "sample.mp4", sample.length(), false), metadataOf(sample), out,
            AudioExtractionSettings(format, 0, mode),
        )
        val result = runBlocking {
            FfmpegKitExecutor().execute("it-audio", DefaultFfmpegCommandBuilder().buildAudioExtractionCommand(request).toArguments()) { _, _ -> }
        }
        assertEquals(FfmpegExecResult.Success, result)
        return out
    }

    @Test fun aac_is_extracted_to_m4a_by_stream_copy() {
        val m = metadataOf(extract(AudioFormat.M4A, AudioExtractionMode.Copy))
        assertTrue(m.videoStreams.isEmpty()); assertEquals("aac", m.audioStreams.single().codec)
        assertTrue("duration ${m.durationMs}", m.durationMs in 1500..2600)
    }

    @Test fun audio_is_extracted_to_wav() {
        val m = metadataOf(extract(AudioFormat.WAV, AudioExtractionMode.Encode()))
        assertEquals("pcm_s16le", m.audioStreams.single().codec); assertTrue(m.durationMs in 1500..2600)
    }

    @Test fun audio_is_extracted_to_mp3_when_lame_is_installed() {
        val encoders = runBlocking { FfmpegKitListRunner(Dispatchers.IO).listEncoders() }
        assumeTrue("libmp3lame not in this build", encoders != null && "libmp3lame" in encoders)
        val m = metadataOf(extract(AudioFormat.MP3, AudioExtractionMode.Encode(bitrateKbps = 192)))
        assertEquals("mp3", m.audioStreams.single().codec)
    }
}
