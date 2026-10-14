package io.bsoft.bconverter.feature.export

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.app.BConverterApplication
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.ffmpeg.FfmpegKitFfprobeRunner
import io.bsoft.bconverter.media.ffmpeg.FfprobeJsonParser
import io.bsoft.bconverter.media.ffmpeg.FfprobeParseResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole chain on a device: registry -> WorkManager -> foreground worker -> real FFmpeg -> publisher. */
class ExportEndToEndTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as BConverterApplication
    private val dir = File(context.cacheDir, "e2e").apply { mkdirs() }

    @Test fun an_audio_extraction_runs_in_the_background_and_is_saved() {
        val sample = IntegrationFixtures.sampleMp4(context)
        val metadata = runBlocking {
            val run = FfmpegKitFfprobeRunner(Dispatchers.IO).run(sample) as FfprobeRunResult.Success
            (FfprobeJsonParser().parse(run.json) as FfprobeParseResult.Parsed).metadata
        }

        val temp = File(dir, "e2e-temp.wav").apply { delete() }
        val destination = File(dir, "e2e-final.wav").apply { delete() }
        val request = AudioExtractionRequest(
            "e2e-1", ResolvedInput(sample, "sample.mp4", sample.length(), false), metadata, temp,
            AudioExtractionSettings(AudioFormat.WAV, 0, AudioExtractionMode.Encode()),
        )
        app.exportRegistry.register(ExportJob("e2e-1", "e2e", ExportTask.Extract(request), Uri.fromFile(destination)))
        app.exportScheduler.enqueue("e2e-1")

        val status = runBlocking { withTimeout(120_000) { app.exportRegistry.status("e2e-1")!!.first { it.isTerminal } } }
        assertEquals(JobStatus.Completed, status)
        assertTrue("destination empty", destination.length() > 0); assertFalse("temp file left behind", temp.exists())
        app.exportRegistry.remove("e2e-1")
    }
}
