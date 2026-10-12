package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversionSessionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val engine = FakeMediaEngine()
    private val resolver = FakeInputResolver()
    private lateinit var session: ConversionSession
    private lateinit var output: File
    private lateinit var input: ResolvedInput
    private lateinit var request: ConversionRequest

    @Before fun setUp() {
        session = ConversionSession(engine, resolver)
        output = tmp.newFile("out.mp4").apply { writeText("partial") }
        input = ResolvedInput(tmp.newFile("in.mp4"), "in.mp4", 10, isStaged = true)
        request = ConversionRequest("job-1", input, TestMedia.metadata(), output, ConversionSettings(ContainerFormat.MP4, VideoPlan.Copy, AudioPlan.Copy))
    }

    @Test fun starts_idle() = assertEquals(ProcessingState.Idle, session.state.value)

    @Test fun successful_conversion_completes_and_keeps_the_output() = runTest {
        engine.processingResult = MediaProcessingResult.Success(output)
        session.run(request)
        assertEquals(ProcessingState.Completed, session.state.value)
        assertTrue(output.exists()); assertEquals(listOf(request), engine.converted)
    }

    @Test fun staged_input_is_released_after_success() = runTest {
        session.run(request); assertEquals(listOf(input), resolver.released)
    }

    @Test fun engine_progress_is_reflected_in_the_running_state() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request) }
        runCurrent()
        assertEquals(ProcessingState.Running(0f, "Starting"), session.state.value)
        engine.emitProgress("job-1", MediaProcessingProgress(4000, 10_000, 0.4f, 1.0, 6000)); runCurrent()
        assertEquals(ProcessingState.Running(0.4f, "Converting"), session.state.value)
        engine.convertGate!!.complete(MediaProcessingResult.Success(output)); job.join()
        assertEquals(ProcessingState.Completed, session.state.value)
    }

    @Test fun progress_without_a_percentage_is_ignored() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request) }; runCurrent()
        engine.emitProgress("job-1", MediaProcessingProgress(4000, null, null, null, null)); runCurrent()
        assertEquals(ProcessingState.Running(0f, "Starting"), session.state.value)
        engine.convertGate!!.complete(MediaProcessingResult.Success(output)); job.join()
    }

    @Test fun engine_failure_marks_failed_and_removes_partial_output() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("ffmpeg exited with 1")
        session.run(request)
        assertTrue(session.state.value is ProcessingState.Failed)
        assertFalse(output.exists()); assertEquals(listOf(input), resolver.released)
    }

    @Test fun failure_message_is_human_readable_and_hides_engine_details() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("ffmpeg exited with 1\n at com.arthenica.Foo.bar(Foo.java:12)")
        session.run(request)
        val message = (session.state.value as ProcessingState.Failed).message
        assertFalse(message.contains("ffmpeg")); assertFalse(message.contains("arthenica"))
    }

    @Test fun unexpected_exception_marks_failed_and_cleans_up() = runTest {
        engine.convertError = IllegalStateException("native crash")
        session.run(request)
        assertTrue(session.state.value is ProcessingState.Failed)
        assertFalse(output.exists()); assertEquals(listOf(input), resolver.released)
    }

    @Test fun user_cancel_stops_the_engine_marks_cancelled_and_cleans_up() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request) }; runCurrent()
        session.cancel(); job.join()
        assertEquals(listOf("job-1"), engine.cancelledJobIds)
        assertEquals(ProcessingState.Cancelled, session.state.value)
        assertFalse(output.exists()); assertEquals(listOf(input), resolver.released)
    }

    @Test fun cancelling_when_nothing_is_running_does_nothing() = runTest {
        session.cancel(); assertTrue(engine.cancelledJobIds.isEmpty()); assertEquals(ProcessingState.Idle, session.state.value)
    }

    @Test fun cancelling_the_calling_coroutine_also_stops_the_engine_and_cleans_up() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request) }; runCurrent()
        job.cancel(); job.join()
        assertEquals(listOf("job-1"), engine.cancelledJobIds)
        assertEquals(ProcessingState.Cancelled, session.state.value)
        assertFalse(output.exists()); assertEquals(listOf(input), resolver.released)
    }

    @Test fun a_second_run_while_running_is_rejected_without_touching_files() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request) }; runCurrent()
        try { session.run(request); fail("expected IllegalStateException") } catch (_: IllegalStateException) {}
        assertTrue(output.exists()); assertTrue(resolver.released.isEmpty())
        engine.convertGate!!.complete(MediaProcessingResult.Success(output)); job.join()
    }

    @Test fun reset_returns_a_finished_session_to_idle_so_it_can_run_again() = runTest {
        session.run(request); session.reset()
        assertEquals(ProcessingState.Idle, session.state.value)
        session.run(request); assertEquals(ProcessingState.Completed, session.state.value)
    }

    @Test fun reset_while_running_is_rejected() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request) }; runCurrent()
        try { session.reset(); fail("expected IllegalStateException") } catch (_: IllegalStateException) {}
        engine.convertGate!!.complete(MediaProcessingResult.Success(output)); job.join()
    }

    @Test fun the_input_can_be_kept_by_the_caller() = runTest {
        session.run(request, releaseInput = false)
        assertTrue(resolver.released.isEmpty())
    }

    @Test fun a_kept_input_is_also_not_released_on_failure_or_cancellation() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("x")
        session.run(request, releaseInput = false); session.reset()
        engine.convertGate = CompletableDeferred()
        val job = launch { session.run(request, releaseInput = false) }; runCurrent()
        session.cancel(); job.join()
        assertTrue(resolver.released.isEmpty())
    }

    // ---- audio extraction runs through the same state machine ----
    private fun extraction() = AudioExtractionRequest(
        "job-2", input, TestMedia.metadata(), output, AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy),
    )

    @Test fun an_extraction_completes_and_releases_the_input() = runTest {
        session.runExtraction(extraction())
        assertEquals(ProcessingState.Completed, session.state.value)
        assertEquals(listOf(extraction().copy(jobId = "job-2")).size, engine.extracted.size); assertEquals(listOf(input), resolver.released)
    }

    @Test fun a_failed_extraction_removes_the_partial_output() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("x")
        session.runExtraction(extraction())
        assertTrue(session.state.value is ProcessingState.Failed); assertFalse(output.exists())
    }

    @Test fun a_cancelled_extraction_stops_the_engine_and_cleans_up() = runTest {
        engine.convertGate = CompletableDeferred()
        val job = launch { session.runExtraction(extraction(), releaseInput = false) }; runCurrent()
        session.cancel(); job.join()
        assertEquals(listOf("job-2"), engine.cancelledJobIds)
        assertEquals(ProcessingState.Cancelled, session.state.value); assertFalse(output.exists()); assertTrue(resolver.released.isEmpty())
    }
}
