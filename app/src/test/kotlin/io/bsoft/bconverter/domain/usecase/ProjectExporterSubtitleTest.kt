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
class ProjectExporterSubtitleTest {
    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()
    private val registry = ExportRegistry()
    private val scheduler = RecordingExportScheduler()
    private lateinit var staging: StagingDirectory
    private lateinit var exporter: ProjectExporter
    private val inputs = listOf(ResolvedInput(File("/tmp/a"), "a", 1, true))
    private val destination = android.net.Uri.parse("content://dest/1")
    private val cue = io.bsoft.bconverter.domain.subtitle.SubtitleCue("c", 1000, 2000, "Hello")
    private fun track(id: String, mode: io.bsoft.bconverter.domain.subtitle.SubtitleExportMode, language: String? = "en") =
        io.bsoft.bconverter.domain.subtitle.SubtitleTrack(id, "Track $id", listOf(cue), language = language, title = "T$id", isDefault = id == "s1", exportMode = mode)
    private fun project(container: io.bsoft.bconverter.domain.model.ContainerFormat, vararg tracks: io.bsoft.bconverter.domain.subtitle.SubtitleTrack) =
        TL.project(TL.timeline(TL.video("a", 10_000)), export = TL.exportSettings().copy(container = container)).copy(subtitleTracks = tracks.toList())
    private val render get() = registry.job("id-1")!!.task as ExportTask.Render
    private val Soft = io.bsoft.bconverter.domain.subtitle.SubtitleExportMode.SOFT
    private val Burned = io.bsoft.bconverter.domain.subtitle.SubtitleExportMode.BURNED
    private val Off = io.bsoft.bconverter.domain.subtitle.SubtitleExportMode.OFF
    private val MKV = io.bsoft.bconverter.domain.model.ContainerFormat.MKV

    @org.junit.Before fun setUp() {
        staging = StagingDirectory(File(tmp.root, "staging"))
        exporter = ProjectExporter(staging, StorageGuard(io.bsoft.bconverter.fakes.FakeFreeSpaceProvider(), staging), ExportJobLauncher(registry, scheduler), SequentialIds())
    }

    @Test fun soft_tracks_are_written_to_files_in_the_codecs_format_with_their_metadata() {
        exporter.start(project(MKV, track("s1", Soft)), destination, inputs)
        val soft = render.subtitles.soft.single()
        assertTrue(soft.file.readText().contains("00:00:01,000 --> 00:00:02,000")); assertEquals(io.bsoft.bconverter.domain.subtitle.SoftSubtitleCodec.SRT, soft.codec)
        assertEquals("en", soft.language); assertEquals("Ts1", soft.title); assertTrue(soft.isDefault); assertEquals(staging.root, soft.file.parentFile)
    }

    @Test fun burned_tracks_are_written_as_ass() {
        exporter.start(project(MKV, track("b", Burned)), destination, inputs)
        val text = render.subtitles.burned.single().file.readText(); assertTrue(text, text.startsWith("[Script Info]") && text.contains("Dialogue:") && text.contains("Hello"))
    }

    @Test fun tracks_that_are_off_write_nothing() {
        exporter.start(project(MKV, track("o", Off)), destination, inputs); assertTrue(render.subtitles.isEmpty); assertTrue(staging.listFiles().none { it.name.endsWith(".srt") || it.name.endsWith(".ass") })
    }

    @Test fun the_track_offset_is_applied_to_the_exported_file() {
        exporter.start(project(MKV, track("s1", Soft).copy(offsetMs = 500)), destination, inputs)
        assertTrue(render.subtitles.soft.single().file.readText().contains("00:00:01,500 --> 00:00:02,500"))
    }

    @Test fun a_container_without_text_subtitles_rejects_soft_tracks_and_schedules_nothing() {
        val start = exporter.start(project(io.bsoft.bconverter.domain.model.ContainerFormat.AVI, track("s1", Soft)), destination, inputs)
        assertEquals(ExportStart.Rejected(ExportRejection.SubtitlesNotSupported(listOf(io.bsoft.bconverter.domain.subtitle.SubtitleExportIssue.SoftNotSupported("s1", io.bsoft.bconverter.domain.model.ContainerFormat.AVI)))), start)
        assertTrue(scheduler.enqueued.isEmpty()); assertTrue(staging.listFiles().isEmpty())
    }

    @Test fun the_job_lists_the_subtitle_files_so_they_are_cleaned_up() {
        exporter.start(project(MKV, track("s1", Soft), track("b", Burned)), destination, inputs)
        val job = registry.job("id-1")!!
        assertEquals(render.subtitles.files.toSet(), job.tempFiles.toSet()); assertEquals(2, job.tempFiles.size)
    }

    @Test fun a_project_without_subtitles_has_none() { exporter.start(project(MKV), destination, inputs); assertTrue(render.subtitles.isEmpty) }

    @Test fun burned_text_uses_a_font_that_exists_on_every_android_device_unless_one_was_chosen() {
        val plain = track("b", Burned).copy(cues = listOf(cue.copy(styleName = "Other")))
        exporter.start(project(MKV, plain), destination, inputs)
        val text = render.subtitles.burned.single().file.readText()
        assertTrue(text, text.contains("Roboto")); assertTrue(text, !text.contains("Arial"))
    }

    @Test fun a_chosen_font_is_kept_for_burning() {
        val styled = track("b", Burned).copy(styles = mapOf("Default" to io.bsoft.bconverter.domain.subtitle.SubtitleStyle(fontFamily = "Lobster")))
        exporter.start(project(MKV, styled), destination, inputs)
        val text = render.subtitles.burned.single().file.readText(); assertTrue(text, text.contains("Lobster") && !text.contains("Arial"))
    }
}
