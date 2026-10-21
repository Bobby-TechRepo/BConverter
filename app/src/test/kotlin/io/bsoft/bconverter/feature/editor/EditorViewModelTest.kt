package io.bsoft.bconverter.feature.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.DefaultMediaCapabilityRepository
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.render.RenderError
import io.bsoft.bconverter.domain.repository.PublishResult
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.ExportRejection
import io.bsoft.bconverter.domain.usecase.ProjectExporter
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.fakes.DirectExportScheduler
import io.bsoft.bconverter.fakes.FakeCapabilitySource
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
import io.bsoft.bconverter.fakes.FakeUriPermissionPersister
import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val engine = FakeMediaEngine()
    private val capabilities = FakeCapabilitySource()
    private val publisher = FakeOutputPublisher()
    private val free = FakeFreeSpaceProvider()
    private val registry = ExportRegistry()
    private val persister = FakeUriPermissionPersister()
    private val scope = CoroutineScope(dispatcher)
    private val ids = SequentialIds()
    private lateinit var staging: StagingDirectory
    private lateinit var staged: ResolvedInput
    private lateinit var staged2: ResolvedInput
    private val uri1: Uri = Uri.parse("content://x/1")
    private val uri2: Uri = Uri.parse("content://x/2")
    private val destination: Uri = Uri.parse("content://dest/1")
    private fun json(name: String) = checkNotNull(javaClass.getResource("/ffprobe/$name")).readText()
    private val videoOnly = """{"streams":[{"index":0,"codec_type":"video","codec_name":"h264","width":640,"height":360}],"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"5.0"}}"""
    private val noDuration = """{"streams":[{"index":0,"codec_type":"video","codec_name":"h264","width":640,"height":360},{"index":1,"codec_type":"audio","codec_name":"aac"}],"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2"}}"""

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        staging = StagingDirectory(File(tmp.root, "staging"))
        staged = ResolvedInput(tmp.newFile("one.mp4"), "one.mp4", 7_340_032, true)
        staged2 = ResolvedInput(tmp.newFile("two.mp4"), "two.mp4", 1_000_000, true)
        resolver.resultFor = { src -> ResolveResult.Success(if ((src as MediaSource.UriSource).uri == uri1) staged else staged2) }
        runner.result = FfprobeRunResult.Success(json("mp4_h264_aac.json"))
        engine.onRender = { _, out -> out.file.writeText("rendered") }
    }
    @After fun tearDown() { scope.cancel(); Dispatchers.resetMain() }

    private fun launcher() = ExportJobLauncher(registry, DirectExportScheduler(scope, ExportJobExecutor(engine, resolver, publisher, registry)))
    private fun vm(): EditorViewModel {
        val launcher = launcher()
        val exporter = ProjectExporter(staging, StorageGuard(free, staging), launcher, ids)
        return EditorViewModel(MediaProber(resolver, runner), resolver, capabilities, exporter, launcher, persister, ids)
    }
    private fun TestScope.withVideo(): EditorViewModel = vm().also { it.onVideoPicked(uri1); runCurrent() }
    private val EditorViewModel.timeline get() = state.value.editor.timeline
    private val EditorViewModel.message get() = state.value.message
    private fun TestScope.exporting(vm: EditorViewModel) { vm.onExportRequested(); runCurrent(); vm.onExportDestinationChosen(destination); runCurrent() }

    // ================= importing =================
    @Test fun starts_with_an_empty_untitled_project() = runTest(dispatcher) {
        val s = vm().state.value
        assertTrue(s.editor.timeline.videoClips.isEmpty()); assertEquals("Untitled project", s.projectName); assertFalse(s.busy); assertNull(s.message)
    }

    @Test fun a_picked_video_is_added_as_a_whole_source_clip_referring_to_its_original_uri() = runTest(dispatcher) {
        val vm = withVideo()
        val clip = vm.timeline.videoClips.single()
        assertEquals("id-1", clip.id); assertEquals(MediaSource.UriSource(uri1), clip.source)
        assertEquals(0L, clip.sourceStartMs); assertEquals(12_345L, clip.sourceEndMs); assertEquals(12_345L, clip.sourceDurationMs)
        assertEquals(listOf(uri1), persister.persisted)
    }

    @Test fun the_screen_is_busy_while_a_file_is_being_probed() = runTest(dispatcher) {
        resolver.gate = CompletableDeferred()
        val vm = vm(); vm.onVideoPicked(uri1); runCurrent()
        assertTrue(vm.state.value.busy)
        resolver.gate!!.complete(Unit); runCurrent()
        assertFalse(vm.state.value.busy)
    }

    @Test fun later_videos_are_appended_after_the_earlier_ones() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent()
        assertEquals(listOf("id-1", "id-2"), vm.timeline.videoClips.map { it.id }); assertEquals(12_345L, vm.timeline.videoClips[1].startTimeMs)
    }

    @Test fun using_the_same_file_twice_stages_it_only_once() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri1); runCurrent()
        assertEquals(2, vm.timeline.videoClips.size); assertEquals(1, resolver.resolved.size)
    }

    @Test fun a_dismissed_picker_changes_nothing() = runTest(dispatcher) {
        val vm = vm(); vm.onVideoPicked(null); runCurrent()
        assertTrue(vm.timeline.videoClips.isEmpty()); assertTrue(persister.persisted.isEmpty())
    }

    @Test fun a_file_without_video_is_rejected_and_its_staged_copy_released() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(json("mp3_with_cover.json"))
        val vm = vm(); vm.onVideoPicked(uri1); runCurrent()
        assertEquals(EditorMessage.NoVideoInFile, vm.message); assertTrue(vm.timeline.videoClips.isEmpty()); assertEquals(listOf(staged), resolver.released)
    }

    @Test fun a_probe_failure_is_reported() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Failure(1)
        val vm = vm(); vm.onVideoPicked(uri1); runCurrent()
        assertEquals(EditorMessage.ProbeFailed(ProbeFailure.ToolFailed(1)), vm.message)
    }

    @Test fun an_unreadable_file_is_reported() = runTest(dispatcher) {
        resolver.resultFor = { ResolveResult.Failure(InputError.NotFound) }
        val vm = vm(); vm.onVideoPicked(uri1); runCurrent()
        assertEquals(EditorMessage.ProbeFailed(ProbeFailure.Input(InputError.NotFound)), vm.message)
    }

    @Test fun a_file_of_unknown_length_cannot_be_edited() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(noDuration)
        val vm = vm(); vm.onVideoPicked(uri1); runCurrent()
        assertEquals(EditorMessage.UnknownDuration, vm.message); assertTrue(vm.timeline.videoClips.isEmpty()); assertEquals(listOf(staged), resolver.released)
    }

    @Test fun picked_audio_is_placed_at_the_playhead_on_a_new_track() = runTest(dispatcher) {
        val vm = withVideo(); vm.seekTo(3_000); vm.onAudioPicked(uri2); runCurrent()
        val clip = vm.timeline.audioClips.single()
        assertEquals(3_000L, clip.startTimeMs); assertEquals("id-2", clip.id); assertEquals(MediaSource.UriSource(uri2), clip.source)
        assertEquals("id-3", vm.timeline.audioTracks.single().id)
    }

    @Test fun a_file_without_audio_cannot_be_added_as_audio() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(videoOnly)
        val vm = vm(); vm.onAudioPicked(uri2); runCurrent()
        assertEquals(EditorMessage.NoAudioInFile, vm.message); assertEquals(listOf(staged2), resolver.released)
    }

    // ================= editing =================
    @Test fun selecting_and_seeking_are_reflected_in_the_state() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.seekTo(5_000)
        assertEquals("id-1", vm.state.value.editor.selectedClipId); assertEquals(5_000L, vm.state.value.editor.playheadMs)
    }

    @Test fun splitting_at_the_playhead_splits_the_selected_clip() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.seekTo(4_000); vm.splitAtPlayhead()
        assertEquals(listOf("id-1", "id-2"), vm.timeline.videoClips.map { it.id }); assertEquals(listOf(4_000L, 8_345L), vm.timeline.videoClips.map { it.durationMs })
    }

    @Test fun an_edit_that_needs_a_selection_explains_itself_when_there_is_none() = runTest(dispatcher) {
        val vm = withVideo()
        vm.splitAtPlayhead(); assertEquals(EditorMessage.EditFailed(EditError.NothingSelected), vm.message)
        vm.dismissMessage(); vm.deleteSelected(); assertEquals(EditorMessage.EditFailed(EditError.NothingSelected), vm.message)
    }

    @Test fun the_selected_clip_can_be_deleted() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.deleteSelected()
        assertTrue(vm.timeline.videoClips.isEmpty()); assertNull(vm.state.value.editor.selectedClipId)
    }

    @Test fun the_selected_clip_can_be_trimmed_and_sped_up() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        vm.trimSelected(2_000, 8_000); assertEquals(6_000L, vm.timeline.videoClips.single().durationMs)
        vm.setSpeed(2f); assertEquals(2f, vm.timeline.videoClips.single().playbackSpeed, 0f)
    }

    @Test fun an_invalid_edit_is_reported_with_the_reason() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.setSpeed(5f)
        assertEquals(EditorMessage.EditFailed(EditError.InvalidSpeed), vm.message)
    }

    @Test fun audio_volume_and_position_can_be_changed() = runTest(dispatcher) {
        val vm = withVideo(); vm.onAudioPicked(uri2); runCurrent(); vm.select("id-2")
        vm.setVolume(0.5f); vm.moveSelectedAudio(1_000)
        val clip = vm.timeline.audioClips.single()
        assertEquals(0.5f, clip.volume, 0f); assertEquals(1_000L, clip.startTimeMs)
    }

    @Test fun a_video_clip_can_be_moved_within_the_track() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent(); vm.select("id-1"); vm.moveSelectedVideo(1)
        assertEquals(listOf("id-2", "id-1"), vm.timeline.videoClips.map { it.id })
    }

    @Test fun undo_and_redo_walk_through_the_edits() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.deleteSelected()
        vm.undo(); assertEquals(1, vm.timeline.videoClips.size); assertTrue(vm.state.value.editor.canRedo)
        vm.redo(); assertTrue(vm.timeline.videoClips.isEmpty())
    }

    @Test fun the_project_can_be_renamed() = runTest(dispatcher) { val vm = vm(); vm.renameProject("Holiday"); assertEquals("Holiday", vm.state.value.projectName) }

    // ================= replacing =================
    @Test fun the_selected_clip_can_be_replaced_by_another_file() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.onReplacePicked(uri2); runCurrent()
        val clip = vm.timeline.videoClips.single()
        assertEquals("id-1", clip.id); assertEquals(MediaSource.UriSource(uri2), clip.source); assertEquals(12_345L, clip.sourceEndMs)
    }

    @Test fun a_replacement_must_suit_the_kind_of_clip() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        runner.result = FfprobeRunResult.Success(json("mp3_with_cover.json")); vm.onReplacePicked(uri2); runCurrent()
        assertEquals(EditorMessage.NoVideoInFile, vm.message); assertEquals(MediaSource.UriSource(uri1), vm.timeline.videoClips.single().source)
        assertEquals(listOf(staged2), resolver.released)
    }

    @Test fun replacing_needs_a_selection() = runTest(dispatcher) {
        val vm = withVideo(); vm.onReplacePicked(uri2); runCurrent()
        assertEquals(EditorMessage.EditFailed(EditError.NothingSelected), vm.message); assertEquals(1, resolver.resolved.size)
    }

    // ================= export: asking =================
    @Test fun the_export_choices_list_what_the_build_can_encode() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertEquals(capabilities.repo.getContainers(), vm.state.value.exportContainers)
    }

    @Test fun export_options_can_be_chosen() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.onExportPresetSelected(ExportPreset.SMALL_FILE); vm.onExportContainerSelected(ContainerFormat.WEBM)
        assertEquals(ExportPreset.SMALL_FILE, vm.state.value.exportPreset); assertEquals(ContainerFormat.WEBM, vm.state.value.exportContainer)
    }

    @Test fun a_container_that_is_not_offered_is_ignored() = runTest(dispatcher) {
        capabilities.repo = io.bsoft.bconverter.fakes.TestMedia.repo(io.bsoft.bconverter.domain.model.VideoCodec.H264, io.bsoft.bconverter.domain.model.AudioCodec.AAC)
        val vm = vm(); runCurrent(); vm.onExportContainerSelected(ContainerFormat.WEBM)
        assertEquals(ContainerFormat.MP4, vm.state.value.exportContainer)
    }

    @Test fun exporting_asks_for_a_destination_named_after_the_project() = runTest(dispatcher) {
        val events = mutableListOf<EditorEvent>()
        val vm = withVideo(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.onExportRequested(); runCurrent()
        assertEquals(listOf<EditorEvent>(EditorEvent.RequestExportDestination("Untitled project.mp4", "video/mp4")), events)
    }

    @Test fun the_chosen_container_decides_the_file_type() = runTest(dispatcher) {
        val events = mutableListOf<EditorEvent>()
        val vm = withVideo(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.onExportContainerSelected(ContainerFormat.WEBM); vm.onExportRequested(); runCurrent()
        assertEquals(listOf<EditorEvent>(EditorEvent.RequestExportDestination("Untitled project.webm", "video/webm")), events)
    }

    @Test fun an_empty_project_cannot_be_exported() = runTest(dispatcher) {
        val events = mutableListOf<EditorEvent>()
        val vm = vm(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.onExportRequested(); runCurrent()
        assertEquals(EditorMessage.ExportRejected(ExportRejection.NotRenderable(RenderError.NoVideo)), vm.message); assertTrue(events.isEmpty())
    }

    @Test fun a_project_name_that_cannot_be_a_file_name_is_refused() = runTest(dispatcher) {
        val events = mutableListOf<EditorEvent>()
        val vm = withVideo(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.renameProject("a/b"); vm.onExportRequested(); runCurrent()
        assertEquals(EditorMessage.InvalidProjectName, vm.message); assertTrue(events.isEmpty())
    }

    @Test fun without_any_encoder_exporting_is_explained() = runTest(dispatcher) {
        capabilities.repo = DefaultMediaCapabilityRepository(emptyList())
        val vm = withVideo(); vm.onExportRequested(); runCurrent()
        assertEquals(EditorMessage.NoExportEncoders, vm.message)
    }

    // ================= export: running =================
    @Test fun an_export_renders_the_project_with_staged_files_and_saves_it() = runTest(dispatcher) {
        val vm = withVideo(); exporting(vm)
        val project = engine.rendered.single().first
        assertEquals(MediaSource.FileSource(staged.file), project.timeline.videoClips.single().source)
        assertEquals("Untitled project", project.name); assertEquals(ContainerFormat.MP4, project.export.container)
        assertEquals("rendered", publisher.published.single().content)
        assertEquals(ExportUiState.Completed, vm.state.value.export); assertTrue(resolver.released.isEmpty())
    }

    @Test fun export_progress_is_visible() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = withVideo(); exporting(vm)
        assertTrue(vm.state.value.export is ExportUiState.Running)
        engine.emitProgress(engine.rendered.single().first.id, MediaProcessingProgress(4000, 12_345, 0.4f, 1.0, 8000)); runCurrent()
        assertEquals(ExportUiState.Running(0.4f), vm.state.value.export)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
    }

    @Test fun a_failed_export_is_reported_and_editing_can_continue() = runTest(dispatcher) {
        engine.processingResult = MediaProcessingResult.Failure("boom")
        val vm = withVideo(); exporting(vm)
        assertEquals(EditorMessage.ExportFailed, vm.message); assertEquals(ExportUiState.Idle, vm.state.value.export)
        assertEquals(1, vm.timeline.videoClips.size)
    }

    @Test fun a_failed_save_is_reported() = runTest(dispatcher) {
        publisher.result = PublishResult.Failure
        val vm = withVideo(); exporting(vm)
        assertEquals(EditorMessage.ExportSaveFailed, vm.message); assertEquals(ExportUiState.Idle, vm.state.value.export)
    }

    @Test fun cancelling_an_export_returns_to_idle_without_an_error() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = withVideo(); exporting(vm)
        vm.onCancelExport(); runCurrent()
        assertEquals(ExportUiState.Idle, vm.state.value.export); assertNull(vm.message)
    }

    @Test fun a_dismissed_destination_dialog_starts_nothing() = runTest(dispatcher) {
        val vm = withVideo(); vm.onExportRequested(); runCurrent(); vm.onExportDestinationChosen(null); runCurrent()
        assertTrue(engine.rendered.isEmpty()); assertEquals(ExportUiState.Idle, vm.state.value.export)
    }

    @Test fun an_export_that_will_not_fit_is_refused_with_the_figures() = runTest(dispatcher) {
        free.available = 1
        val vm = withVideo(); exporting(vm)
        assertTrue((vm.message as EditorMessage.ExportRejected).reason is ExportRejection.InsufficientStorage); assertTrue(engine.rendered.isEmpty())
    }

    // ================= leaving =================
    private fun clear(vm: EditorViewModel) {
        val store = ViewModelStore()
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })[EditorViewModel::class.java]
        store.clear()
    }

    @Test fun leaving_without_an_export_releases_every_staged_copy() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent()
        clear(vm)
        assertEquals(listOf(staged, staged2), resolver.released)
    }

    @Test fun leaving_during_an_export_lets_the_job_keep_its_inputs_and_frees_the_rest() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = withVideo(); exporting(vm)
        vm.onVideoPicked(uri2); runCurrent()                  // added after the export started
        clear(vm)
        assertEquals(listOf(staged2), resolver.released)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
        assertEquals(listOf(staged2, staged), resolver.released); assertEquals(1, publisher.published.size)
    }

    @Test fun dismissing_clears_the_message_and_the_completed_banner() = runTest(dispatcher) {
        val vm = withVideo(); exporting(vm)
        assertEquals(ExportUiState.Completed, vm.state.value.export)
        vm.dismissMessage()
        assertEquals(ExportUiState.Idle, vm.state.value.export); assertNull(vm.message)
    }
}
