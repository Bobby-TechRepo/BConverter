package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.render.RenderError
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import io.bsoft.bconverter.fakes.RecordingExportScheduler
import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.fakes.TL
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectExporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val registry = ExportRegistry()
    private val scheduler = RecordingExportScheduler()
    private val free = FakeFreeSpaceProvider()
    private lateinit var staging: StagingDirectory
    private lateinit var exporter: ProjectExporter
    private val destination: Uri = Uri.parse("content://dest/1")
    private val inputs = listOf(ResolvedInput(File("/tmp/a"), "a", 1, true))
    private val project get() = TL.project(TL.timeline(TL.video("a", 10_000)))

    @Before fun setUp() {
        staging = StagingDirectory(File(tmp.root, "staging"))
        exporter = ProjectExporter(staging, StorageGuard(free, staging), ExportJobLauncher(registry, scheduler), SequentialIds())
    }

    @Test fun a_renderable_project_becomes_a_queued_job() {
        val start = exporter.start(project, destination, inputs) as ExportStart.Started
        assertEquals("id-1", start.jobId); assertEquals(JobStatus.Queued, start.status.value); assertEquals(listOf("id-1"), scheduler.enqueued)
    }

    @Test fun the_job_renders_into_a_temp_file_in_the_staging_directory() {
        exporter.start(project, destination, inputs)
        val job = registry.job("id-1")!!; val task = job.task as ExportTask.Render
        assertEquals(staging.root, task.outputFile.parentFile); assertTrue(task.outputFile.name.endsWith(".mp4"))
        assertEquals("Project", job.title); assertEquals(destination, job.destination); assertEquals(inputs, task.inputs)
    }

    @Test fun the_rendered_project_carries_the_job_id_so_progress_and_cancel_are_unambiguous() {
        exporter.start(project, destination, inputs)
        assertEquals("id-1", (registry.job("id-1")!!.task as ExportTask.Render).project.id)
    }

    @Test fun a_project_that_cannot_be_rendered_is_rejected_and_nothing_is_scheduled() {
        val start = exporter.start(project.copy(timeline = Timeline()), destination, inputs)
        assertEquals(ExportStart.Rejected(ExportRejection.NotRenderable(RenderError.NoVideo)), start)
        assertTrue(scheduler.enqueued.isEmpty()); assertNull(registry.job("id-1"))
    }

    @Test fun a_render_that_cannot_fit_is_rejected_with_the_space_figures() {
        free.available = 1
        val rejected = (exporter.start(project, destination, inputs) as ExportStart.Rejected).reason as ExportRejection.InsufficientStorage
        assertEquals(1L, rejected.availableBytes); assertTrue(rejected.requiredBytes > StorageGuard.HEADROOM_BYTES)
        assertTrue(scheduler.enqueued.isEmpty())
    }
}
