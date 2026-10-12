package io.bsoft.bconverter.feature.export

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.fakes.FakeExportNotifier
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExportWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val engine = FakeMediaEngine()
    private val resolver = FakeInputResolver()
    private val publisher = FakeOutputPublisher()
    private val registry = ExportRegistry()
    private val notifier = FakeExportNotifier()
    private val executor = ExportJobExecutor(engine, resolver, publisher, registry)
    private lateinit var output: File

    @Before fun setUp() {
        output = File(tmp.root, "out.tmp")
        val input = ResolvedInput(tmp.newFile("in.mp4"), "in.mp4", 10, true)
        registry.register(
            ExportJob(
                "job-1", "holiday",
                ExportTask.Convert(ConversionRequest("job-1", input, TestMedia.metadata(), output, ConversionSettings(ContainerFormat.MP4, VideoPlan.Copy, AudioPlan.Copy))),
                Uri.parse("content://dest/1"),
            ),
        )
        engine.onConvert = { it.outputFile.writeText("converted") }
    }

    private fun worker(input: Data): ExportWorker =
        TestListenableWorkerBuilder<ExportWorker>(context, inputData = input)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    ExportWorker(appContext, workerParameters, executor, registry, notifier)
            }).build()
    private fun forJob(id: String = "job-1") = worker(workDataOf(ExportWorker.KEY_JOB_ID to id))

    @Test fun a_completed_job_succeeds_and_is_reported() = runTest {
        assertEquals(ListenableWorker.Result.success(), forJob().doWork())
        assertEquals(JobStatus.Completed, registry.status("job-1")!!.value)
        assertEquals(listOf(FakeExportNotifier.Result("holiday", JobStatus.Completed)), notifier.results)
    }

    @Test fun the_job_runs_as_foreground_work_titled_after_the_file() = runTest {
        forJob().doWork()
        assertEquals(FakeExportNotifier.Foreground("holiday", null), notifier.foreground.first())
    }

    @Test fun progress_updates_the_foreground_notification() = runTest {
        engine.convertGate = CompletableDeferred()
        val result = async { forJob().doWork() }; runCurrent()
        engine.emitProgress("job-1", MediaProcessingProgress(4000, 10_000, 0.4f, 1.0, 6000)); runCurrent()
        assertTrue(notifier.foreground.any { it.progress == 0.4f })
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x")))
        assertEquals(ListenableWorker.Result.success(), result.await())
    }

    @Test fun a_failed_conversion_fails_the_work_and_is_reported() = runTest {
        engine.processingResult = MediaProcessingResult.Failure("boom")
        assertEquals(ListenableWorker.Result.failure(), forJob().doWork())
        assertEquals(JobStatus.Failed(JobFailure.ConversionFailed), notifier.results.single().status)
    }

    @Test fun a_job_the_process_no_longer_knows_fails_quietly() = runTest {
        assertEquals(ListenableWorker.Result.failure(), forJob("unknown").doWork())
        assertTrue(notifier.results.isEmpty()); assertTrue(engine.converted.isEmpty())
    }

    @Test fun missing_input_data_fails() = runTest {
        assertEquals(ListenableWorker.Result.failure(), worker(Data.EMPTY).doWork())
    }
}
