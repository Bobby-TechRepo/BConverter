package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.domain.media.WaveformFailure
import io.bsoft.bconverter.domain.media.WaveformResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegWaveformGeneratorIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tmp = File(context.cacheDir, "waveform-test")
    private val generator = FfmpegWaveformGenerator(FfmpegKitExecutor(), tmp)

    @org.junit.Before fun cleanWorkDir() { tmp.deleteRecursively() }

    @Test fun the_sample_tone_has_about_twenty_peaks_a_second_and_is_audible() = runBlocking {
        val r = generator.generate(IntegrationFixtures.sampleMp4(context)) as WaveformResult.Success
        assertEquals(40.0, r.waveform.peaks.size.toDouble(), 3.0)
        assertTrue("loudest peak was ${r.waveform.peaks.max()}", r.waveform.peaks.max() > 0.1f)
        assertTrue(tmp.listFiles().orEmpty().isEmpty())
    }

    @Test fun a_file_that_is_not_audio_fails_cleanly() = runBlocking {
        val bad = File(context.cacheDir, "waveform-input/bad.mp3").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(2_000) { (it * 31).toByte() }) }
        val r = generator.generate(bad)
        assertEquals(WaveformResult.Failure(WaveformFailure.Unreadable), r)
    }
}
