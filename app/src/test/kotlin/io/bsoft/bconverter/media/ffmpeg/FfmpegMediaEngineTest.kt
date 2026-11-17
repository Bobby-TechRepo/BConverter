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
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngineException
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    @Test fun a_failed_run_carries_the_end_of_the_ffmpeg_log() = runTest {
        executor.result = FfmpegExecResult.Failure(1, "Unknown encoder 'libx264'\n")
        val r = engine.convert(request) as MediaProcessingResult.Failure
        assertTrue(r.message.contains("Unknown encoder 'libx264'"))
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
        "job-1", request.input, request.metadata.copy(subtitleStreams = listOf(io.bsoft.bconverter.domain.model.SubtitleStream(2, "eng", "subrip"))), out, AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy),
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

    // ---- rendering ----
    private fun project(id: String = "proj-1") = TL.project(TL.timeline(TL.video("a", 10_000))).copy(id = id)
    private fun renderOutput() = RenderOutput(File(tmp.root, "final.mp4"))

    @Test fun rendering_runs_the_built_command_under_the_project_id() = runTest {
        val out = renderOutput().also { it.file.writeText("data") }
        engine.render(project(), out)
        val expected = DefaultFfmpegCommandBuilder().buildRenderCommand(project(), out).toArguments()
        assertEquals(listOf(FakeFfmpegExecutor.Call("proj-1", expected)), executor.calls)
    }

    @Test fun a_render_with_an_output_file_is_success() = runTest {
        val out = renderOutput().also { it.file.writeText("data") }
        assertEquals(MediaProcessingResult.Success(out.file), engine.render(project(), out))
    }

    @Test fun a_render_without_an_output_file_is_a_failure() = runTest {
        assertTrue(engine.render(project(), renderOutput()) is MediaProcessingResult.Failure)
    }

    @Test fun a_project_that_cannot_be_planned_fails_without_running_ffmpeg() = runTest {
        assertTrue(engine.render(project().copy(timeline = Timeline()), renderOutput()) is MediaProcessingResult.Failure)
        assertTrue(executor.calls.isEmpty())
    }

    @Test fun render_progress_is_measured_against_the_project_duration() = runTest {
        val out = renderOutput().also { it.file.writeText("data") }
        executor.statistics = listOf(2500L to 2.0)
        val first = async { engine.observeProgress("proj-1").first() }
        runCurrent()
        engine.render(project(), out)
        assertEquals(MediaProcessingProgress(2500, 10_000, 0.25f, 2.0, 3750), first.await())
    }

    // ---- rendering in parts (step 18) ----
    /** Writes each run's output file (the last argument), like a real FFmpeg run. */
    private class WritingExecutor : FfmpegExecutor {
        val calls = mutableListOf<List<String>>()
        var failAt: Int? = null
        override suspend fun execute(jobId: String, args: List<String>, onStatistics: (Long, Double?) -> Unit): FfmpegExecResult {
            calls += args
            if (failAt == calls.size) return FfmpegExecResult.Failure(1, "boom")
            onStatistics(1_000, 1.0)
            File(args.last()).writeText("data")
            return FfmpegExecResult.Success
        }
        override suspend fun cancel(jobId: String) {}
    }

    private fun longProject() = TL.project(TL.timeline(TL.video("a", 4_000), TL.video("b", 4_000), TL.video("c", 4_000))).copy(id = "proj-1")

    @Test fun a_long_project_renders_its_parts_joins_them_then_runs_the_final_pass() = runTest {
        val writing = WritingExecutor()
        val parted = FfmpegMediaEngine(MediaProber(resolver, runner), resolver, writing, maxClipsPerPart = 2) { DefaultFfmpegCommandBuilder() }
        val out = renderOutput()
        assertEquals(MediaProcessingResult.Success(out.file), parted.render(longProject(), out))
        assertEquals(4, writing.calls.size) // two parts, the join, the final pass
        assertTrue(writing.calls[0].last().endsWith("final.part1.mkv"))
        assertTrue(writing.calls[1].last().endsWith("final.part2.mkv"))
        assertTrue(writing.calls[2].containsAll(listOf("concat", "copy")))
        assertTrue(writing.calls[3].contains(File(tmp.root, "final.joined.mkv").absolutePath))
        assertEquals(out.file.absolutePath, writing.calls[3].last())
        // Only the export is left.
        assertEquals(listOf("final.mp4"), tmp.root.list()!!.filter { it.startsWith("final") })
    }

    @Test fun a_failed_part_stops_the_render_and_cleans_up() = runTest {
        val writing = WritingExecutor().apply { failAt = 2 }
        val parted = FfmpegMediaEngine(MediaProber(resolver, runner), resolver, writing, maxClipsPerPart = 2) { DefaultFfmpegCommandBuilder() }
        val result = parted.render(longProject(), renderOutput()) as MediaProcessingResult.Failure
        assertTrue(result.message.contains("part 2 of 2"))
        assertEquals(2, writing.calls.size)
        assertTrue(tmp.root.list()!!.none { it.startsWith("final") })
    }

    @Test fun progress_runs_over_the_parts_and_the_final_pass() = runTest {
        val writing = WritingExecutor()
        val parted = FfmpegMediaEngine(MediaProber(resolver, runner), resolver, writing, maxClipsPerPart = 2) { DefaultFfmpegCommandBuilder() }
        val seen = mutableListOf<MediaProcessingProgress>()
        val collector = backgroundScope.launch { parted.observeProgress("proj-1").collect { seen += it } }
        runCurrent()
        parted.render(longProject(), renderOutput())
        runCurrent(); collector.cancel()
        // 12 s of video: the parts are the first 12 s of 24, the final pass the rest.
        assertTrue(seen.all { it.totalDurationMs == 24_000L })
        assertTrue(seen.any { it.processedDurationMs == 13_000L })
    }

    // ---- subtitle extraction ----
    private fun subtitleExtraction(out: File = File(tmp.root, "out.srt")) = io.bsoft.bconverter.media.api.SubtitleExtractionRequest(
        "job-1", request.input, request.metadata.copy(subtitleStreams = listOf(io.bsoft.bconverter.domain.model.SubtitleStream(2, "eng", "subrip"))), out,
        io.bsoft.bconverter.domain.model.SubtitleExtractionSettings(0, io.bsoft.bconverter.domain.model.SubtitleOutput.SRT),
    )

    @Test fun subtitle_extraction_runs_the_built_command_under_the_job_id() = runTest {
        val req = subtitleExtraction().also { it.outputFile.writeText("1\n") }
        assertEquals(MediaProcessingResult.Success(req.outputFile), engine.extractSubtitle(req))
        assertEquals(listOf(FakeFfmpegExecutor.Call("job-1", DefaultFfmpegCommandBuilder().buildSubtitleExtractionCommand(req).toArguments())), executor.calls)
    }

    @Test fun subtitle_extraction_without_output_failure_and_cancellation_are_mapped() = runTest {
        assertTrue(engine.extractSubtitle(subtitleExtraction()) is MediaProcessingResult.Failure)
        executor.result = FfmpegExecResult.Failure(2)
        assertTrue((engine.extractSubtitle(subtitleExtraction()) as MediaProcessingResult.Failure).message.contains("2"))
        executor.result = FfmpegExecResult.Cancelled
        assertEquals(MediaProcessingResult.Cancelled, engine.extractSubtitle(subtitleExtraction()))
    }
}
