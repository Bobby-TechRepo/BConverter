package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.repository.PublishResult
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportJobExecutorTest {
    @get:Rule val tmp = TemporaryFolder()
    private val engine = FakeMediaEngine()
    private val resolver = FakeInputResolver()
    private val publisher = FakeOutputPublisher()
    private val registry = ExportRegistry()
    private val executor = ExportJobExecutor(engine, resolver, publisher, registry)
    private val destination: Uri = Uri.parse("content://dest/1")
    private lateinit var input: ResolvedInput
    private lateinit var output: File

    @Before fun setUp() {
        input = ResolvedInput(tmp.newFile("in.mp4"), "in.mp4", 10, isStaged = true)
        output = File(tmp.root, "out.tmp").apply { writeText("partial") }
    }

    private fun convertJob(id: String = "job-1") = ExportJob(
        id, "holiday",
        ExportTask.Convert(ConversionRequest(id, input, TestMedia.metadata(), output, ConversionSettings(ContainerFormat.MP4, VideoPlan.Copy, AudioPlan.Copy))),
        destination,
    ).also { registry.register(it) }
    private fun extractJob(id: String = "job-1") = ExportJob(
        id, "song",
        ExportTask.Extract(AudioExtractionRequest(id, input, TestMedia.metadata(), output, AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy))),
        destination,
    ).also { registry.register(it) }

    // ---- success ----
    @Test fun a_conversion_is_run_published_and_cleaned_up() = runTest {
        engine.onConvert = { it.outputFile.writeText("converted") }
        convertJob()
        assertEquals(JobStatus.Completed, executor.execute("job-1"))
        val published = publisher.published.single()
        assertEquals(output, published.file); assertEquals(destination, published.destination); assertEquals("converted", published.content)
        assertFalse(output.exists()); assertEquals(JobStatus.Completed, registry.status("job-1")!!.value)
        assertEquals(1, engine.converted.size)
    }

    @Test fun an_audio_extraction_is_run_the_same_way() = runTest {
        engine.onExtract = { it.outputFile.writeText("audio") }
        extractJob()
        assertEquals(JobStatus.Completed, executor.execute("job-1"))
        assertEquals("audio", publisher.published.single().content); assertEquals(1, engine.extracted.size)
    }

    @Test fun an_attached_job_keeps_the_input_for_its_owner() = runTest {
        convertJob(); executor.execute("job-1"); assertTrue(resolver.released.isEmpty())
    }

    // ---- progress and publishing ----
    @Test fun engine_progress_becomes_running_status() = runTest {
        engine.convertGate = CompletableDeferred(); convertJob()
        val run = launch { executor.execute("job-1") }; runCurrent()
        engine.emitProgress("job-1", MediaProcessingProgress(4000, 10_000, 0.4f, 1.0, 6000)); runCurrent()
        assertEquals(JobStatus.Running(0.4f), registry.status("job-1")!!.value)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); run.join()
    }

    @Test fun publishing_is_visible_as_its_own_status() = runTest {
        publisher.gate = CompletableDeferred(); convertJob()
        val run = launch { executor.execute("job-1") }; runCurrent()
        assertEquals(JobStatus.Publishing, registry.status("job-1")!!.value)
        publisher.gate!!.complete(Unit); run.join()
        assertEquals(JobStatus.Completed, registry.status("job-1")!!.value)
    }

    // ---- failures ----
    @Test fun an_engine_failure_fails_the_job_without_publishing() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("boom"); convertJob()
        assertEquals(JobStatus.Failed(JobFailure.ConversionFailed), executor.execute("job-1"))
        assertTrue(publisher.published.isEmpty()); assertFalse(output.exists())
    }

    @Test fun a_failed_save_fails_the_job_and_removes_the_temp_file() = runTest {
        publisher.result = PublishResult.Failure; engine.onConvert = { it.outputFile.writeText("c") }; convertJob()
        assertEquals(JobStatus.Failed(JobFailure.SaveFailed), executor.execute("job-1")); assertFalse(output.exists())
    }

    @Test fun an_unknown_job_is_reported_as_interrupted() = runTest {
        assertEquals(JobStatus.Failed(JobFailure.Interrupted), executor.execute("nope"))
        assertTrue(engine.converted.isEmpty())
    }

    @Test fun a_failed_attached_job_keeps_its_input_for_a_retry() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("x"); convertJob(); executor.execute("job-1")
        assertTrue(resolver.released.isEmpty())
    }

    // ---- cancellation ----
    @Test fun cancelling_stops_the_engine_marks_cancelled_and_cleans_up() = runTest {
        engine.convertGate = CompletableDeferred(); convertJob()
        val run = launch { executor.execute("job-1") }; runCurrent()
        run.cancel(); run.join()
        assertEquals(listOf("job-1"), engine.cancelledJobIds)
        assertEquals(JobStatus.Cancelled, registry.status("job-1")!!.value)
        assertFalse(output.exists()); assertTrue(resolver.released.isEmpty())
    }

    @Test fun cancelling_while_publishing_cleans_up() = runTest {
        publisher.gate = CompletableDeferred(); convertJob()
        val run = launch { executor.execute("job-1") }; runCurrent()
        run.cancel(); run.join()
        assertEquals(JobStatus.Cancelled, registry.status("job-1")!!.value); assertFalse(output.exists())
    }

    // ---- detached jobs (the screen is gone) ----
    @Test fun a_detached_job_releases_the_input_and_forgets_itself_when_done() = runTest {
        engine.convertGate = CompletableDeferred(); engine.onConvert = { it.outputFile.writeText("c") }; convertJob()
        val run = launch { executor.execute("job-1") }; runCurrent()
        registry.detach("job-1")
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); run.join()
        assertEquals(listOf(input), resolver.released); assertNull(registry.job("job-1"))
    }

    @Test fun a_detached_job_also_cleans_up_when_it_fails_or_is_cancelled() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("x"); convertJob(); registry.detach("job-1")
        executor.execute("job-1")
        assertEquals(listOf(input), resolver.released); assertNull(registry.job("job-1"))
    }
}
