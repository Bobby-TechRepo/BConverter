package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.fakes.FakeFfmpegExecutor
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngineException
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FfmpegMediaEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val executor = FakeFfmpegExecutor()
    private val engine = FfmpegMediaEngine(MediaProber(resolver, runner), resolver, executor) { DefaultFfmpegCommandBuilder() }
    private lateinit var output: File
    private lateinit var request: ConversionRequest

    @Before fun setUp() {
        output = File(tmp.root, "out.mkv")
        request = ConversionRequest(
            "job-1", ResolvedInput(tmp.newFile("in.mp4"), "in.mp4", 10, true), TestMedia.metadata(), output,
            ConversionSettings(ContainerFormat.MKV, VideoPlan.Copy, AudioPlan.Copy),
        )
    }

    // ---- convert ----
    @Test fun convert_runs_the_built_command_under_the_job_id() = runTest {
        output.writeText("data")
        engine.convert(request)
        val expected = DefaultFfmpegCommandBuilder().buildConversionCommand(request).toArguments()
        assertEquals(listOf(FakeFfmpegExecutor.Call("job-1", expected)), executor.calls)
    }

    @Test fun successful_run_with_an_output_file_is_success() = runTest {
        output.writeText("data")
        assertEquals(MediaProcessingResult.Success(output), engine.convert(request))
    }

    @Test fun success_without_a_usable_output_file_is_a_failure() = runTest {
        assertTrue(engine.convert(request) is MediaProcessingResult.Failure)
        output.writeText("")
        assertTrue(engine.convert(request) is MediaProcessingResult.Failure)
    }

    @Test fun failed_run_reports_the_exit_code() = runTest {
        executor.result = FfmpegExecResult.Failure(3)
        val r = engine.convert(request) as MediaProcessingResult.Failure
        assertTrue(r.message.contains("3"))
    }

    @Test fun cancelled_run_is_cancelled() = runTest {
        executor.result = FfmpegExecResult.Cancelled
        assertEquals(MediaProcessingResult.Cancelled, engine.convert(request))
    }

    @Test fun statistics_are_published_as_progress_for_the_job() = runTest {
        output.writeText("data")
        executor.statistics = listOf(2500L to 2.0)
        val first = async { engine.observeProgress("job-1").first() }
        runCurrent()
        engine.convert(request)
        assertEquals(MediaProcessingProgress(2500, 10_000, 0.25f, 2.0, 3750), first.await())
    }

    @Test fun cancel_is_forwarded_to_the_executor() = runTest {
        engine.cancel("job-1"); assertEquals(listOf("job-1"), executor.cancelled)
    }

    // ---- probe ----
    @Test fun probe_returns_metadata_and_releases_the_staged_input() = runTest {
        runner.result = FfprobeRunResult.Success(checkNotNull(javaClass.getResource("/ffprobe/mp4_h264_aac.json")).readText())
        val metadata = engine.probe(MediaSource.FileSource(File("/tmp/a.mp4")))
        assertEquals(12345L, metadata.durationMs)
        assertEquals(1, resolver.released.size)
    }

    @Test fun probe_failure_is_thrown_as_a_media_engine_exception() = runTest {
        runner.result = FfprobeRunResult.Success("garbage")
        try { engine.probe(MediaSource.FileSource(File("/tmp/a.mp4"))); fail("expected exception") }
        catch (e: MediaEngineException) { assertEquals(ProbeFailure.InvalidOutput, e.failure) }
    }

    // ---- audio extraction ----
    private fun extraction(out: File = File(tmp.root, "out.m4a")) = AudioExtractionRequest(
        "job-1", request.input, request.metadata, out, AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy),
    )

    @Test fun extraction_runs_the_built_command_under_the_job_id() = runTest {
        val req = extraction().also { it.outputFile.writeText("data") }
        engine.extractAudio(req)
        val expected = DefaultFfmpegCommandBuilder().buildAudioExtractionCommand(req).toArguments()
        assertEquals(listOf(FakeFfmpegExecutor.Call("job-1", expected)), executor.calls)
    }

    @Test fun extraction_with_an_output_file_is_success() = runTest {
        val req = extraction().also { it.outputFile.writeText("data") }
        assertEquals(MediaProcessingResult.Success(req.outputFile), engine.extractAudio(req))
    }

    @Test fun extraction_without_an_output_file_is_a_failure() = runTest {
        assertTrue(engine.extractAudio(extraction()) is MediaProcessingResult.Failure)
    }

    @Test fun extraction_failure_and_cancellation_are_mapped() = runTest {
        executor.result = FfmpegExecResult.Failure(2)
        assertTrue((engine.extractAudio(extraction()) as MediaProcessingResult.Failure).message.contains("2"))
        executor.result = FfmpegExecResult.Cancelled
        assertEquals(MediaProcessingResult.Cancelled, engine.extractAudio(extraction()))
    }

    @Test fun extraction_statistics_are_published_as_progress() = runTest {
        val req = extraction().also { it.outputFile.writeText("data") }
        executor.statistics = listOf(2500L to 2.0)
        val first = async { engine.observeProgress("job-1").first() }
        runCurrent()
        engine.extractAudio(req)
        assertEquals(MediaProcessingProgress(2500, 10_000, 0.25f, 2.0, 3750), first.await())
    }
}
