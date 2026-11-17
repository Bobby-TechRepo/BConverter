package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.media.WaveformFailure
import io.bsoft.bconverter.domain.media.WaveformResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegWaveformGeneratorTest {
    private val dir = Files.createTempDirectory("fw").toFile().also { it.deleteOnExit() }
    private val source = File(dir, "in.mp3").apply { writeBytes(ByteArray(4)) }

    private class Executor(var result: FfmpegExecResult = FfmpegExecResult.Success, var pcm: ByteArray? = ByteArray(0)) : FfmpegExecutor {
        var args: List<String> = emptyList()
        override suspend fun execute(jobId: String, args: List<String>, onStatistics: (Long, Double?) -> Unit): FfmpegExecResult {
            this.args = args
            pcm?.let { File(args.last()).writeBytes(it) }
            return result
        }
        override suspend fun cancel(jobId: String) {}
    }

    private fun generator(e: Executor) = FfmpegWaveformGenerator(e, File(dir, "tmp"), sampleRate = 100, peaksPerSecond = 10)

    @Test fun decodes_to_mono_raw_pcm_without_video() = runBlocking {
        val e = Executor(); generator(e).generate(source)
        val a = e.args
        assertTrue(a.containsAll(listOf("-nostdin", "-vn")))
        assertEquals("1", a[a.indexOf("-ac") + 1]); assertEquals("100", a[a.indexOf("-ar") + 1]); assertEquals("s16le", a[a.indexOf("-f") + 1])
        assertEquals(source.absolutePath, a[a.indexOf("-i") + 1])
    }

    @Test fun the_pcm_is_reduced_to_peaks_and_the_temp_file_removed() = runBlocking {
        val pcm = ByteArray(40) { if (it % 2 == 1) 0x40 else 0 } // 20 samples of 0x4000
        val e = Executor(pcm = pcm)
        val r = generator(e).generate(source) as WaveformResult.Success
        assertEquals(2, r.waveform.peaks.size); assertEquals(0.5f, r.waveform.peaks[0], 0.001f)
        assertFalse(File(e.args.last()).exists())
    }

    @Test fun a_source_without_audio_output_is_an_empty_waveform() = runBlocking {
        val r = generator(Executor(pcm = ByteArray(0))).generate(source) as WaveformResult.Success
        assertTrue(r.waveform.peaks.isEmpty())
    }

    @Test fun a_missing_source_fails_without_running_ffmpeg() = runBlocking {
        val e = Executor()
        assertEquals(WaveformResult.Failure(WaveformFailure.SourceMissing), generator(e).generate(File(dir, "gone.mp3")))
        assertTrue(e.args.isEmpty())
    }

    @Test fun an_ffmpeg_failure_is_unreadable_and_leaves_no_temp_file() = runBlocking {
        val e = Executor(result = FfmpegExecResult.Failure(1), pcm = ByteArray(4))
        assertEquals(WaveformResult.Failure(WaveformFailure.Unreadable), generator(e).generate(source))
        assertFalse(File(e.args.last()).exists())
    }

    @Test fun cancellation_propagates() {
        val e = Executor(result = FfmpegExecResult.Cancelled)
        try { runBlocking { generator(e).generate(source) }; throw AssertionError("expected cancellation") } catch (_: CancellationException) {}
    }
}
