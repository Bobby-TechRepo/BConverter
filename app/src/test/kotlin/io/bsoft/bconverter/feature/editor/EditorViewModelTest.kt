package io.bsoft.bconverter.feature.editor

import android.net.Uri
import io.bsoft.bconverter.domain.model.SavedOutput
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
import io.bsoft.bconverter.domain.timeline.SeekRange
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
import io.bsoft.bconverter.fakes.FakeProjectRepository
import io.bsoft.bconverter.domain.project.ProjectLoadResult
import io.bsoft.bconverter.domain.project.ProjectOpener
import io.bsoft.bconverter.domain.project.MissingSource
import io.bsoft.bconverter.domain.project.StoredProject
import io.bsoft.bconverter.domain.project.TimeSource
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
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
import kotlinx.coroutines.runBlocking
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
    private val projects = FakeProjectRepository()
    private val unavailable = mutableSetOf<MediaSource>()
    private var now = 1_000L
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
    private val contentReader = io.bsoft.bconverter.fakes.FakeContentReader()
    private val imageImporter = io.bsoft.bconverter.fakes.FakeOverlayImageImporter()
    private fun vm(saved: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()): EditorViewModel {
        val launcher = launcher()
        val exporter = ProjectExporter(staging, StorageGuard(free, staging), launcher, ids)
        return EditorViewModel(
            MediaProber(resolver, runner), resolver, capabilities, exporter, launcher, persister, ids,
            projects, ProjectOpener(projects) { it !in unavailable }, TimeSource { now },
            io.bsoft.bconverter.domain.usecase.SubtitleImporter(contentReader, ids, Dispatchers.Unconfined),
            imageImporter, saved,
        )
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

    @Test fun a_video_added_while_the_playhead_is_in_the_blank_area_still_joins_directly_after_the_last_clip() = runTest(dispatcher) {
        val vm = withVideo(); vm.seekTo(20_000); runCurrent()
        assertEquals(20_000L, vm.state.value.editor.playheadMs)
        vm.onVideoPicked(uri2); runCurrent()
        assertEquals(12_345L, vm.timeline.videoClips[1].startTimeMs)
    }

    @Test fun the_playhead_can_be_sought_into_the_blank_area_but_not_beyond_it() = runTest(dispatcher) {
        val vm = withVideo()
        vm.seekTo(99_999_999); runCurrent()
        assertEquals(12_345L + SeekRange.BLANK_TAIL_MS, vm.state.value.editor.playheadMs)
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

    // ================= steps 23 and 24 =================
    @Test fun dragging_an_edge_trims_the_clip_in_one_undo_step() = runTest(dispatcher) {
        val vm = withVideo()
        val before = vm.timeline.videoClips.single().durationMs
        vm.trimEdge("id-1", io.bsoft.bconverter.domain.timeline.TrimEdge.END, -1_000)
        assertEquals(before - 1_000, vm.timeline.videoClips.single().durationMs)
        vm.undo(); assertEquals(before, vm.timeline.videoClips.single().durationMs)
    }

    @Test fun several_clips_can_be_picked_and_deleted_together() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent()
        vm.select("id-1"); vm.startMultiSelect()
        assertEquals(setOf("id-1"), vm.state.value.multiSelection)
        vm.toggleInSelection("id-2"); vm.toggleInSelection("id-1"); vm.toggleInSelection("id-1")
        assertEquals(setOf("id-1", "id-2"), vm.state.value.multiSelection)
        vm.deleteMultiSelection(ripple = true)
        assertTrue(vm.timeline.videoClips.isEmpty()); assertNull(vm.state.value.multiSelection)
        vm.undo(); assertEquals(2, vm.timeline.videoClips.size)
    }

    @Test fun deleting_an_empty_pick_says_nothing_is_selected() = runTest(dispatcher) {
        val vm = withVideo(); vm.startMultiSelect(); vm.deleteMultiSelection(ripple = false)
        assertEquals(EditorMessage.EditFailed(EditError.NothingSelected), vm.message)
        vm.endMultiSelect(); assertNull(vm.state.value.multiSelection)
    }

    @Test fun ripple_delete_pulls_the_music_back_with_the_picture() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent()
        vm.onAudioPicked(uri2); runCurrent()
        val music = vm.timeline.audioClips.single()
        vm.select(music.id); vm.moveSelectedAudio(12_000)
        val first = vm.timeline.videoClips.first { it.id == "id-1" }.durationMs
        vm.select("id-1"); vm.rippleDeleteSelected()
        assertEquals((12_000L - first).coerceAtLeast(0L), vm.timeline.audioClips.single().startTimeMs)
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

    @Test fun text_that_cannot_be_drawn_stops_the_export_with_its_own_message() = runTest(dispatcher) {
        // The test setup has no overlay renderer, so an export that has text cannot draw it and must say so.
        val vm = withVideo(); vm.addTextOverlay(0); exporting(vm)
        assertEquals(EditorMessage.ExportOverlaysFailed, vm.message); assertEquals(ExportUiState.Idle, vm.state.value.export)
        assertEquals(1, vm.overlays.size)
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

    // ================= preview timeline =================
    @Test fun the_preview_timeline_refers_to_the_staged_files_while_the_project_keeps_the_original_uris() = runTest(dispatcher) {
        val vm = withVideo()
        assertEquals(MediaSource.UriSource(uri1), vm.timeline.videoClips.single().source)
        assertEquals(MediaSource.FileSource(staged.file), vm.state.value.previewTimeline.videoClips.single().source)
    }

    @Test fun a_missing_source_stays_unstaged_in_the_preview_timeline() = runTest(dispatcher) {
        saveExisting(savedClip); unavailable += MediaSource.UriSource(uri1)
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(MediaSource.UriSource(uri1), vm.state.value.previewTimeline.videoClips.single().source)
    }

    // ================= autosave (spec §41) =================
    private val EditorViewModel.saved get() = (runBlocking { projects.load(state.value.projectId) } as? ProjectLoadResult.Loaded)?.stored

    @Test fun an_import_is_autosaved_under_the_projects_id() = runTest(dispatcher) {
        val vm = withVideo()
        assertEquals(listOf(vm.state.value.projectId), runBlocking { projects.list() }.map { it.id })
        assertEquals(vm.timeline, vm.saved!!.project.timeline)
        assertEquals("Untitled project", vm.saved!!.project.name)
    }

    @Test fun a_new_project_with_nothing_in_it_is_not_saved() = runTest(dispatcher) {
        val vm = vm(); vm.renameProject("Ghost"); runCurrent()
        assertTrue(runBlocking { projects.list() }.isEmpty())
    }

    @Test fun renaming_a_project_that_has_content_is_saved() = runTest(dispatcher) {
        val vm = withVideo(); vm.renameProject("Holiday"); runCurrent()
        assertEquals("Holiday", vm.saved!!.project.name)
    }

    @Test fun edits_undo_and_redo_are_saved() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.deleteSelected(); runCurrent()
        assertTrue(vm.saved!!.project.timeline.videoClips.isEmpty())
        vm.undo(); runCurrent(); assertEquals(1, vm.saved!!.project.timeline.videoClips.size)
        vm.redo(); runCurrent(); assertTrue(vm.saved!!.project.timeline.videoClips.isEmpty())
    }

    @Test fun a_rejected_edit_is_not_saved() = runTest(dispatcher) {
        val vm = withVideo(); val before = projects.saveCount
        vm.select("id-1"); vm.setSpeed(5f); runCurrent()
        assertEquals(before, projects.saveCount)
    }

    @Test fun the_export_choice_is_saved_with_the_project() = runTest(dispatcher) {
        val vm = withVideo(); runCurrent()
        vm.onExportContainerSelected(ContainerFormat.WEBM); runCurrent()
        assertEquals(ContainerFormat.WEBM, vm.saved!!.project.export.container)
    }

    @Test fun a_failed_autosave_is_reported_and_the_next_change_saves_everything() = runTest(dispatcher) {
        projects.failNextSave = true
        val vm = withVideo()
        assertEquals(EditorMessage.ProjectSaveFailed, vm.message); assertNull(vm.saved)
        vm.renameProject("Retry"); runCurrent()
        assertEquals("Retry", vm.saved!!.project.name); assertEquals(1, vm.saved!!.project.timeline.videoClips.size)
    }

    // ================= reopening =================
    private val savedClip = VideoClip("c1", MediaSource.UriSource(uri1), 12_345, 0, 12_345)
    private fun saveExisting(vararg clips: VideoClip) = runBlocking {
        val project = VideoProject("saved-1", "Holiday", Timeline(clips.toList()).normalized(), TL.exportSettings())
        projects.save(StoredProject(project, createdAtMs = 100, modifiedAtMs = 200))
    }

    @Test fun opening_a_saved_project_restores_its_timeline_name_and_id() = runTest(dispatcher) {
        saveExisting(savedClip)
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(listOf(savedClip), vm.timeline.videoClips); assertEquals("Holiday", vm.state.value.projectName)
        assertEquals("saved-1", vm.state.value.projectId); assertTrue(vm.state.value.missingSources.isEmpty()); assertFalse(vm.state.value.busy)
        assertNull(vm.message)
    }

    @Test fun opening_stages_the_sources_so_preview_and_export_work() = runTest(dispatcher) {
        saveExisting(savedClip)
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(1, resolver.resolved.size)
        exporting(vm)
        assertEquals(MediaSource.FileSource(staged.file), engine.rendered.single().first.timeline.videoClips.single().source)
    }

    @Test fun opening_alone_does_not_rewrite_the_project_but_later_edits_keep_its_creation_time() = runTest(dispatcher) {
        saveExisting(savedClip)
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(1, projects.saveCount) // only the seeding save
        vm.select("c1"); vm.setSpeed(2f); runCurrent()
        assertEquals(100L, vm.saved!!.createdAtMs); assertEquals(1_000L, vm.saved!!.modifiedAtMs)
        assertEquals(2f, vm.saved!!.project.timeline.videoClips.single().playbackSpeed, 0f)
    }

    @Test fun a_missing_source_is_reported_and_its_clip_stays_in_the_timeline() = runTest(dispatcher) {
        saveExisting(savedClip); unavailable += MediaSource.UriSource(uri1)
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(listOf(MissingSource(MediaSource.UriSource(uri1), listOf("c1"))), vm.state.value.missingSources)
        assertEquals(1, vm.timeline.videoClips.size); assertTrue(resolver.resolved.isEmpty())
    }

    @Test fun a_source_that_cannot_be_prepared_is_reported_as_missing() = runTest(dispatcher) {
        saveExisting(savedClip)
        resolver.resultFor = { ResolveResult.Failure(InputError.NotFound) }
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(listOf("c1"), vm.state.value.missingSources.single().clipIds); assertEquals(1, vm.timeline.videoClips.size)
    }

    @Test fun a_project_with_missing_media_cannot_be_exported_until_it_is_fixed() = runTest(dispatcher) {
        saveExisting(savedClip); unavailable += MediaSource.UriSource(uri1)
        val vm = vm(); vm.openProject("saved-1"); runCurrent(); vm.onExportRequested(); runCurrent()
        assertTrue(vm.message is EditorMessage.ExportRejected); assertTrue(engine.rendered.isEmpty())
    }

    @Test fun replacing_a_missing_clip_removes_it_from_the_missing_list() = runTest(dispatcher) {
        saveExisting(savedClip); unavailable += MediaSource.UriSource(uri1)
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        vm.select("c1"); vm.onReplacePicked(uri2); runCurrent()
        assertTrue(vm.state.value.missingSources.isEmpty())
    }

    @Test fun an_unknown_project_is_reported() = runTest(dispatcher) {
        val vm = vm(); vm.openProject("nope"); runCurrent()
        assertEquals(EditorMessage.ProjectNotFound, vm.message); assertFalse(vm.state.value.busy)
    }

    @Test fun a_damaged_project_is_reported_and_leaves_the_editor_empty() = runTest(dispatcher) {
        saveExisting(savedClip); projects.corrupt("saved-1")
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertEquals(EditorMessage.ProjectCorrupt, vm.message); assertTrue(vm.timeline.videoClips.isEmpty())
    }

    @Test fun the_busy_flag_is_on_while_a_project_is_being_prepared() = runTest(dispatcher) {
        saveExisting(savedClip); resolver.gate = CompletableDeferred()
        val vm = vm(); vm.openProject("saved-1"); runCurrent()
        assertTrue(vm.state.value.busy)
        resolver.gate!!.complete(Unit); runCurrent(); assertFalse(vm.state.value.busy)
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

    // ---- sharing (spec Phase 16) ----
    @Test fun a_finished_export_remembers_where_it_was_saved_so_it_can_be_shared() = runTest(dispatcher) {
        val vm = withVideo(); assertNull(vm.state.value.savedExport)
        exporting(vm)
        assertEquals(SavedOutput(destination, ContainerFormat.MP4.mimeType), vm.state.value.savedExport)
    }

    @Test fun dismissing_the_completed_banner_clears_the_shareable_output() = runTest(dispatcher) {
        val vm = withVideo(); exporting(vm); vm.dismissMessage()
        assertNull(vm.state.value.savedExport)
    }

    @Test fun starting_another_export_clears_the_previous_shareable_output() = runTest(dispatcher) {
        val vm = withVideo(); exporting(vm)
        engine.convertGate = CompletableDeferred()
        exporting(vm)
        assertNull(vm.state.value.savedExport)
    }

    // ---- subtitles ----
    private val srtUri: Uri = Uri.parse("content://docs/talk.srt")
    private val srtText = "1\n00:00:01,000 --> 00:00:02,000\nOne\n\n2\n00:00:03,000 --> 00:00:04,000\nTwo\n"
    private fun TestScope.withSubtitles(): EditorViewModel = withVideo().also {
        contentReader.put(srtUri, io.bsoft.bconverter.fakes.FakeContentReader.Entry(io.bsoft.bconverter.domain.model.ContentInfo("talk.srt", null, srtText.length.toLong()), srtText.toByteArray()))
        it.onSubtitlePicked(srtUri); runCurrent()
    }
    private val EditorViewModel.track get() = state.value.subtitleTracks.single()

    @Test fun importing_a_subtitle_file_adds_a_track_and_saves_it_with_the_project() = runTest(dispatcher) {
        val vm = withSubtitles()
        assertEquals("talk", vm.track.name); assertEquals(listOf("One", "Two"), vm.track.cues.map { it.text })
        assertEquals(listOf("talk"), vm.saved!!.project.subtitleTracks.map { it.name })
    }

    @Test fun a_file_that_is_not_a_subtitle_is_reported_and_adds_nothing() = runTest(dispatcher) {
        val vm = withVideo()
        contentReader.put(srtUri, io.bsoft.bconverter.fakes.FakeContentReader.Entry(io.bsoft.bconverter.domain.model.ContentInfo("notes.txt", null, 5), "hello".toByteArray()))
        vm.onSubtitlePicked(srtUri); runCurrent()
        assertTrue(vm.message is EditorMessage.SubtitleImportFailed); assertTrue(vm.state.value.subtitleTracks.isEmpty())
    }

    @Test fun a_dismissed_subtitle_picker_changes_nothing() = runTest(dispatcher) { val vm = withVideo(); vm.onSubtitlePicked(null); runCurrent(); assertTrue(vm.state.value.subtitleTracks.isEmpty()) }

    @Test fun editing_a_cue_updates_the_track_and_the_saved_project() = runTest(dispatcher) {
        val vm = withSubtitles(); val cue = vm.track.cues.first()
        vm.editCueText(vm.track.id, cue.id, "Changed"); vm.retimeCue(vm.track.id, cue.id, 500, 1500); runCurrent()
        assertEquals("Changed", vm.track.cues.first().text); assertEquals(500L, vm.track.cues.first().startMs)
        assertEquals("Changed", vm.saved!!.project.subtitleTracks.single().cues.first().text)
    }

    @Test fun an_invalid_retime_is_refused_and_reported() = runTest(dispatcher) {
        val vm = withSubtitles(); val cue = vm.track.cues.first()
        vm.retimeCue(vm.track.id, cue.id, 2000, 1000)
        assertEquals(EditorMessage.SubtitleEditRejected(io.bsoft.bconverter.domain.subtitle.EditProblem.InvalidTiming), vm.message); assertEquals(1000L, vm.track.cues.first().startMs)
    }

    @Test fun adding_a_cue_at_the_playhead_accounts_for_the_track_offset_and_selects_it() = runTest(dispatcher) {
        val vm = withSubtitles(); vm.setSubtitleOffset(vm.track.id, 1_000)
        vm.addCueAtPlayhead(vm.track.id, 10_000)
        val added = vm.track.cues.last()
        assertEquals(9_000L, added.startMs); assertEquals(11_000L, added.endMs); assertEquals(added.id, vm.state.value.selectedCue?.cueId)
    }

    @Test fun split_duplicate_merge_and_delete_work_on_the_selected_track() = runTest(dispatcher) {
        val vm = withSubtitles(); val t = vm.track.id; val first = vm.track.cues.first().id
        vm.splitCueAtPlayhead(t, first, 1_500); assertEquals(3, vm.track.cues.size)
        vm.mergeCueWithNext(t, first); assertEquals(2, vm.track.cues.size)
        vm.duplicateCue(t, first); assertEquals(3, vm.track.cues.size)
        vm.deleteCue(t, first); assertEquals(2, vm.track.cues.size)
    }

    @Test fun removing_a_track_removes_its_selection_too() = runTest(dispatcher) {
        val vm = withSubtitles(); vm.selectCue(vm.track.id, vm.track.cues.first().id)
        vm.removeSubtitleTrack(vm.track.id)
        assertTrue(vm.state.value.subtitleTracks.isEmpty()); assertNull(vm.state.value.selectedCue)
    }

    @Test fun reopening_a_project_restores_its_subtitle_tracks() = runTest(dispatcher) {
        val vm = withSubtitles(); runCurrent()
        val id = vm.state.value.projectId
        val reopened = vm(); reopened.openProject(id); runCurrent()
        assertEquals(listOf("One", "Two"), reopened.track.cues.map { it.text })
    }

    // ================= text overlays =================
    private val EditorViewModel.overlays get() = state.value.textOverlays
    private val EditorViewModel.overlay get() = state.value.textOverlays.single()

    @Test fun adding_text_puts_a_selected_caption_at_the_playhead() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(2_000)
        assertEquals(2_000L, vm.overlay.startMs); assertEquals(5_000L, vm.overlay.endMs); assertEquals(vm.overlay.id, vm.state.value.selectedOverlayId)
    }

    @Test fun text_overlays_are_autosaved_and_restored_when_the_project_is_reopened() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.setOverlayText(vm.overlay.id, "Hello"); runCurrent()
        assertEquals("Hello", vm.saved!!.project.textOverlays.single().text)
        val reopened = vm(); reopened.openProject(vm.state.value.projectId); runCurrent()
        assertEquals("Hello", reopened.overlay.text); assertNull(reopened.state.value.selectedOverlayId)
    }

    @Test fun a_project_with_only_text_is_still_saved() = runTest(dispatcher) {
        val vm = vm(); vm.addTextOverlay(0); runCurrent()
        assertEquals(1, vm.saved!!.project.textOverlays.size)
    }

    @Test fun an_invalid_value_is_refused_and_reported_without_changing_the_overlay() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val before = vm.overlay
        vm.setOverlayScale(before.id, -1f)
        assertEquals(EditorMessage.OverlayEditRejected(io.bsoft.bconverter.domain.overlay.OverlayProblem.InvalidValue), vm.message); assertEquals(before, vm.overlay)
        vm.retimeOverlay(before.id, 500, 500)
        assertEquals(EditorMessage.OverlayEditRejected(io.bsoft.bconverter.domain.overlay.OverlayProblem.InvalidTiming), vm.message)
    }

    @Test fun style_position_rotation_and_animation_changes_apply() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id
        vm.updateOverlayStyle(id) { it.copy(fontWeight = 900) }; vm.setOverlayPosition(id, 0.2f, 0.9f); vm.setOverlayRotation(id, 370f)
        vm.setOverlayAnimations(id, io.bsoft.bconverter.domain.overlay.OverlayAnimation(io.bsoft.bconverter.domain.overlay.AnimationKind.FADE), null)
        val o = vm.overlay
        assertEquals(900, o.style.fontWeight); assertEquals(0.2f, o.centerX); assertEquals(0.9f, o.centerY); assertEquals(10f, o.rotationDeg, 0.001f); assertEquals("fade", o.animIn!!.kind.id)
    }

    @Test fun keyframes_can_be_set_and_undone_and_a_bad_one_is_refused() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id
        vm.setOverlayKeyframes(id, listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.2f, 0.2f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(500, 0.8f, 0.8f, 1.5f, 0.5f)))
        assertEquals(2, vm.overlay.keyframes.size)
        vm.setOverlayKeyframes(id, listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(99_000, 0.2f, 0.2f)))
        assertEquals(EditorMessage.OverlayEditRejected(io.bsoft.bconverter.domain.overlay.OverlayProblem.InvalidValue), vm.message); assertEquals(2, vm.overlay.keyframes.size)
        vm.undo(); assertEquals(0, vm.overlay.keyframes.size)
    }

    @Test fun dragging_a_keyframed_overlay_edits_the_keyframe_at_the_playhead_and_saves_at_the_end() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id
        vm.setOverlayKeyframes(id, listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.5f, 0.5f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(1_000, 0.5f, 0.5f)))
        vm.transformOverlayKeyframe(id, 500, 0.1f, 0f, 1f)
        vm.transformOverlayKeyframe(id, 500, 0.1f, 0f, 1f)   // the second step edits the keyframe the first one made
        assertEquals(listOf(0L, 500L, 1_000L), vm.overlay.keyframes.map { it.offsetMs }); assertEquals(0.7f, vm.overlay.keyframes[1].x, 0.0001f)
        assertEquals(0.5f, vm.overlay.centerX, 0f)   // the base position did not change
        vm.endOverlayGesture()
    }
    @Test fun twisting_a_keyframed_overlay_turns_the_keyframe_at_the_playhead() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id
        vm.setOverlayKeyframes(id, listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.5f, 0.5f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(1_000, 0.5f, 0.5f)))
        vm.transformOverlayKeyframe(id, 500, 0f, 0f, 1f, 30f)
        vm.transformOverlayKeyframe(id, 500, 0f, 0f, 1f, 15f)
        assertEquals(45f, vm.overlay.keyframes[1].rotationDeg, 0.0001f)
        assertEquals(0f, vm.overlay.rotationDeg, 0f)   // the overlay's own rotation did not change
        vm.endOverlayGesture()
    }
    @Test fun a_drag_on_an_overlay_without_keyframes_does_nothing_here() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val before = vm.overlay
        vm.transformOverlayKeyframe(before.id, 100, 0.1f, 0f, 1f); assertEquals(before, vm.overlay)
    }
    @Test fun duplicate_delete_and_layer_order() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val first = vm.overlay.id
        vm.duplicateOverlay(first); val second = vm.state.value.selectedOverlayId!!
        assertEquals(listOf(first, second), vm.overlays.map { it.id })
        vm.moveOverlayLayer(first, EditorViewModel.OverlayLayerMove.FRONT); assertEquals(listOf(second, first), vm.overlays.map { it.id })
        vm.deleteOverlay(second); assertEquals(listOf(first), vm.overlays.map { it.id }); assertNull(vm.state.value.selectedOverlayId)
    }

    @Test fun a_drag_moves_the_selected_overlay_shows_the_guide_at_the_middle_and_is_saved_when_it_ends() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id; vm.setOverlayPosition(id, 0.4f, 0.2f)
        vm.transformSelectedOverlay(0.095f, 0f, 1f, 0f)
        assertEquals(0.5f, vm.overlay.centerX); assertTrue(vm.state.value.overlayGuide.vertical); assertFalse(vm.state.value.overlayGuide.horizontal)
        vm.endOverlayGesture(); runCurrent()
        assertFalse(vm.state.value.overlayGuide.vertical); assertEquals(0.5f, vm.saved!!.project.textOverlays.single().centerX)
    }

    @Test fun pinch_and_twist_scale_and_rotate_the_selected_overlay() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0)
        vm.transformSelectedOverlay(0f, 0f, 2f, 30f)
        assertEquals(2f, vm.overlay.scale, 0.0001f); assertEquals(30f, vm.overlay.rotationDeg, 0.001f)
    }

    @Test fun a_gesture_with_nothing_selected_does_nothing() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.selectOverlay(null); val before = vm.overlay
        vm.transformSelectedOverlay(0.3f, 0.3f, 2f, 40f); assertEquals(before, vm.overlay)
    }

    @Test fun selecting_an_unknown_overlay_selects_nothing() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.selectOverlay("nope"); assertNull(vm.state.value.selectedOverlayId)
    }

    @Test fun the_preview_frame_follows_the_first_clips_shape() = runTest(dispatcher) {
        val vm = withVideo(); assertTrue(vm.state.value.frameAspect > 0f)
    }

    // ================= undo / redo across timeline, subtitles and text =================
    @Test fun undo_and_redo_cover_text_overlay_edits() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id; vm.setOverlayText(id, "Hello")
        vm.undo(); assertEquals("Text", vm.overlay.text); assertTrue(vm.state.value.editor.canUndo); assertTrue(vm.state.value.editor.canRedo)
        vm.undo(); assertTrue(vm.overlays.isEmpty()); assertNull(vm.state.value.selectedOverlayId)
        vm.redo(); vm.redo(); assertEquals("Hello", vm.overlay.text); assertFalse(vm.state.value.editor.canRedo)
    }

    @Test fun one_undo_button_steps_back_through_timeline_and_text_edits_in_order() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.select("id-1"); vm.deleteSelected()
        assertTrue(vm.timeline.videoClips.isEmpty())
        vm.undo(); assertEquals(1, vm.timeline.videoClips.size); assertEquals(1, vm.overlays.size)
        vm.undo(); assertTrue(vm.overlays.isEmpty()); assertEquals(1, vm.timeline.videoClips.size)
        vm.redo(); assertEquals(1, vm.overlays.size); vm.redo(); assertTrue(vm.timeline.videoClips.isEmpty())
    }

    @Test fun a_whole_drag_is_one_undo_step() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id; vm.setOverlayPosition(id, 0.4f, 0.2f)
        vm.transformSelectedOverlay(0.01f, 0f, 1f, 0f); vm.transformSelectedOverlay(0.01f, 0f, 1f, 0f); vm.transformSelectedOverlay(0.01f, 0f, 1f, 0f); vm.endOverlayGesture()
        assertTrue(vm.overlay.centerX > 0.42f)
        vm.undo(); assertEquals(0.4f, vm.overlay.centerX, 0.0001f)
    }

    @Test fun undo_in_the_middle_of_a_drag_commits_it_and_then_undoes_it() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); val id = vm.overlay.id; vm.setOverlayPosition(id, 0.4f, 0.2f)
        vm.transformSelectedOverlay(0.05f, 0f, 1f, 0f); vm.undo()
        assertEquals(0.4f, vm.overlay.centerX, 0.0001f); assertNull(vm.state.value.overlayGuide.takeIf { it.vertical || it.horizontal })
    }

    @Test fun undoing_text_edits_is_saved() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.setOverlayText(vm.overlay.id, "Hello"); vm.undo(); runCurrent()
        assertEquals("Text", vm.saved!!.project.textOverlays.single().text)
    }

    @Test fun subtitle_edits_can_be_undone() = runTest(dispatcher) {
        val vm = vm(); vm.addEmptySubtitleTrack(); assertEquals(1, vm.state.value.subtitleTracks.size)
        vm.undo(); assertTrue(vm.state.value.subtitleTracks.isEmpty()); vm.redo(); assertEquals(1, vm.state.value.subtitleTracks.size)
    }

    @Test fun a_new_edit_after_undo_clears_redo() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.undo(); assertTrue(vm.state.value.editor.canRedo)
        vm.addTextOverlay(1_000); assertFalse(vm.state.value.editor.canRedo)
    }

    @Test fun reopening_a_project_starts_with_a_fresh_history() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); runCurrent()
        val reopened = vm(); reopened.openProject(vm.state.value.projectId); runCurrent()
        assertFalse(reopened.state.value.editor.canUndo)
        reopened.addTextOverlay(2_000); reopened.undo(); assertEquals(1, reopened.overlays.size)
    }

    @Test fun the_selected_clip_can_get_a_filter_and_undo_it() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        val warm = io.bsoft.bconverter.domain.filter.ClipFilter(temperature = 0.4f)
        vm.setFilter(warm); assertEquals(warm, vm.timeline.videoClips.single().filter)
        vm.undo(); assertEquals(io.bsoft.bconverter.domain.filter.ClipFilter.None, vm.timeline.videoClips.single().filter)
        vm.redo(); assertEquals(warm, vm.timeline.videoClips.single().filter)
    }

    @Test fun apply_to_all_copies_the_look_to_every_clip() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent()
        val warm = io.bsoft.bconverter.domain.filter.ClipFilter(temperature = 0.4f)
        vm.applyFilterToAll(warm)
        assertEquals(listOf(warm, warm), vm.timeline.videoClips.map { it.filter })
    }

    @Test fun a_filter_survives_closing_and_reopening_the_project() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        val look = io.bsoft.bconverter.domain.filter.FilterPresets.byId("noir")!!.filter
        vm.setFilter(look); runCurrent()
        val reopened = vm(); reopened.openProject(vm.state.value.projectId); runCurrent()
        assertEquals(look, reopened.timeline.videoClips.single().filter)
    }

    @Test fun the_selected_clip_can_be_turned_and_undone() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        val t = io.bsoft.bconverter.domain.transform.ClipTransform(rotationDegrees = 90, flipHorizontal = true)
        vm.setTransform(t); assertEquals(t, vm.timeline.videoClips.single().transform)
        vm.undo(); assertEquals(io.bsoft.bconverter.domain.transform.ClipTransform.None, vm.timeline.videoClips.single().transform)
        vm.redo(); assertEquals(t, vm.timeline.videoClips.single().transform)
    }

    @Test fun a_transform_can_be_applied_to_every_clip() = runTest(dispatcher) {
        val vm = withVideo(); vm.onVideoPicked(uri2); runCurrent()
        val t = io.bsoft.bconverter.domain.transform.ClipTransform(rotationDegrees = 270)
        vm.applyTransformToAll(t)
        assertEquals(listOf(t, t), vm.timeline.videoClips.map { it.transform })
    }

    @Test fun turning_the_first_clip_changes_the_frame_shape_overlays_are_placed_on() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); runCurrent()
        val before = vm.state.value.frameAspect
        vm.setTransform(io.bsoft.bconverter.domain.transform.ClipTransform(rotationDegrees = 90)); runCurrent()
        assertEquals(1f / before, vm.state.value.frameAspect, 0.001f)
    }

    @Test fun a_transform_survives_closing_and_reopening_the_project() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        val t = io.bsoft.bconverter.domain.transform.ClipTransform(io.bsoft.bconverter.domain.transform.CropRect(0.1f, 0f, 1f, 1f), 180)
        vm.setTransform(t); runCurrent()
        val reopened = vm(); reopened.openProject(vm.state.value.projectId); runCurrent()
        assertEquals(t, reopened.timeline.videoClips.single().transform)
    }

    @Test fun the_selected_clips_sound_can_be_shaped_and_undone() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        val s = io.bsoft.bconverter.domain.audio.ClipSound(0.5f, 1_000, 2_000)
        vm.setSound(s); val c = vm.timeline.videoClips.single()
        assertEquals(s, io.bsoft.bconverter.domain.audio.ClipSound(c.volume, c.fadeInMs, c.fadeOutMs))
        vm.undo(); assertEquals(1f, vm.timeline.videoClips.single().volume, 0f)
    }

    @Test fun sound_settings_survive_closing_and_reopening_the_project() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        vm.setSound(io.bsoft.bconverter.domain.audio.ClipSound(1.5f, 500, 0)); runCurrent()
        val reopened = vm(); reopened.openProject(vm.state.value.projectId); runCurrent()
        assertEquals(1.5f, reopened.timeline.videoClips.single().volume, 0f); assertEquals(500L, reopened.timeline.videoClips.single().fadeInMs)
    }

    @Test fun the_selected_clip_can_be_reversed_and_undone() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        vm.trimSelected(0, 2_000) // the sample clip is about 4 s, longer than a 1080p clip may be to reverse
        vm.toggleReverse(); assertEquals(true, vm.timeline.videoClips.single().reversed)
        vm.toggleReverse(); assertEquals(false, vm.timeline.videoClips.single().reversed)
        vm.undo(); assertEquals(true, vm.timeline.videoClips.single().reversed)
    }

    @Test fun a_clip_too_long_to_reverse_is_refused_with_the_allowed_length() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1")
        vm.toggleReverse()
        assertEquals(false, vm.timeline.videoClips.single().reversed)
        val m = vm.state.value.message as EditorMessage.ReverseTooLong
        assertEquals(io.bsoft.bconverter.domain.render.ReversePolicy.maxDurationMs(1920, 1080, 30.0), m.maxDurationMs)
    }

    @Test fun reversing_without_a_selection_says_so() = runTest(dispatcher) {
        val vm = withVideo(); vm.select(null); vm.toggleReverse()
        assertEquals(EditorMessage.EditFailed(io.bsoft.bconverter.domain.timeline.EditError.NothingSelected), vm.state.value.message)
    }

    @Test fun a_freeze_frame_is_inserted_at_the_playhead_and_undone() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.seekTo(3_000)
        vm.freezeFrameAtPlayhead()
        assertEquals(listOf(false, true, false), vm.timeline.videoClips.map { it.isFreeze })
        assertEquals(io.bsoft.bconverter.domain.timeline.FreezeFrame.DEFAULT_MS, vm.timeline.videoClips[1].durationMs)
        vm.undo(); assertEquals(1, vm.timeline.videoClips.size)
    }

    @Test fun a_freeze_frames_length_can_be_changed() = runTest(dispatcher) {
        val vm = withVideo(); vm.select("id-1"); vm.seekTo(3_000); vm.freezeFrameAtPlayhead()
        vm.select(vm.timeline.videoClips[1].id); vm.setFreezeLength(4_000)
        assertEquals(4_000L, vm.timeline.videoClips[1].durationMs)
    }

    // ================= picture in picture =================
    private val EditorViewModel.pips get() = timeline.pipClips
    private fun TestScope.withPip(): EditorViewModel = withVideo().also { it.seekTo(2_000); it.onPipPicked(uri2); runCurrent() }

    @Test fun a_picked_video_becomes_a_selected_picture_in_picture_clip_at_the_playhead() = runTest(dispatcher) {
        val vm = withPip()
        val pip = vm.pips.single()
        assertEquals(2_000L, pip.startTimeMs); assertEquals(MediaSource.UriSource(uri2), pip.source)
        assertEquals(pip.id, vm.state.value.editor.selectedClipId)
        assertEquals(1, vm.timeline.videoClips.size)
    }
    @Test fun a_file_without_video_is_not_accepted_as_picture_in_picture() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(json("mp3_with_cover.json"))
        val vm = withVideo(); vm.onPipPicked(uri2); runCurrent()
        assertEquals(EditorMessage.NoVideoInFile, vm.message); assertTrue(vm.pips.isEmpty())
    }
    @Test fun a_dismissed_picker_changes_nothing_for_picture_in_picture() = runTest(dispatcher) {
        val vm = withVideo(); vm.onPipPicked(null); runCurrent()
        assertTrue(vm.pips.isEmpty())
    }
    @Test fun adding_picture_in_picture_can_be_undone_and_redone() = runTest(dispatcher) {
        val vm = withPip()
        vm.undo(); assertTrue(vm.pips.isEmpty())
        vm.redo(); assertEquals(1, vm.pips.size)
    }
    @Test fun the_layout_and_volume_of_the_selected_clip_can_be_changed() = runTest(dispatcher) {
        val vm = withPip()
        val layout = io.bsoft.bconverter.domain.timeline.PipLayout(0.5f, 0.5f, 0.5f, 0.6f)
        vm.setPipLayout(layout); vm.setPipVolume(0.4f)
        assertEquals(layout, vm.pips.single().layout); assertEquals(0.4f, vm.pips.single().volume, 0.001f)
    }
    @Test fun the_border_and_fades_of_the_selected_clip_can_be_set_and_undone() = runTest(dispatcher) {
        val vm = withPip()
        vm.setPipLayout(vm.pips.single().layout.copy(borderWidth = 0.05f, borderRgb = 0xFF0000)); vm.setPipFades(500, 900)
        assertEquals(0.05f, vm.pips.single().layout.borderWidth, 0f); assertEquals(500L, vm.pips.single().fadeInMs); assertEquals(900L, vm.pips.single().fadeOutMs)
        vm.undo(); assertEquals(0L, vm.pips.single().fadeInMs); assertEquals(0.05f, vm.pips.single().layout.borderWidth, 0f)
    }
    @Test fun a_chroma_key_can_be_set_cleared_and_undone() = runTest(dispatcher) {
        val vm = withPip()
        vm.setPipChromaKey(io.bsoft.bconverter.domain.timeline.ChromaKey(0x0000FF)); assertEquals(0x0000FF, vm.pips.single().chromaKey?.rgb)
        vm.setPipChromaKey(null); assertEquals(null, vm.pips.single().chromaKey)
        vm.undo(); assertEquals(0x0000FF, vm.pips.single().chromaKey?.rgb)
    }
    @Test fun keyframes_can_be_set_replace_a_glide_and_be_undone() = runTest(dispatcher) {
        val vm = withPip()
        vm.setPipMotion(io.bsoft.bconverter.domain.timeline.PipMotion(0.9f, 0.9f))
        vm.setPipKeyframes(listOf(io.bsoft.bconverter.domain.timeline.PipKeyframe(0, 0.2f, 0.2f, 0.3f, 1f), io.bsoft.bconverter.domain.timeline.PipKeyframe(500, 0.8f, 0.8f, 0.5f, 0.5f)))
        assertEquals(2, vm.pips.single().keyframes.size); assertEquals(null, vm.pips.single().motion)
        vm.undo(); assertEquals(0, vm.pips.single().keyframes.size); assertEquals(0.9f, vm.pips.single().motion?.endX)
    }
    @Test fun a_shape_can_be_set_and_undone() = runTest(dispatcher) {
        val vm = withPip()
        vm.setPipShape(io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE); assertEquals(io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE, vm.pips.single().shape)
        vm.undo(); assertEquals(io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE, vm.pips.single().shape)
    }
    @Test fun a_speed_can_be_set_and_undone() = runTest(dispatcher) {
        val vm = withPip()
        vm.setPipSpeed(2f); assertEquals(2f, vm.pips.single().speed)
        vm.undo(); assertEquals(1f, vm.pips.single().speed)
    }
    @Test fun a_motion_can_be_set_cleared_and_undone() = runTest(dispatcher) {
        val vm = withPip()
        vm.setPipMotion(io.bsoft.bconverter.domain.timeline.PipMotion(0.2f, 0.8f)); assertEquals(0.2f, vm.pips.single().motion?.endX)
        vm.setPipMotion(null); assertEquals(null, vm.pips.single().motion)
        vm.undo(); assertEquals(0.2f, vm.pips.single().motion?.endX)
    }
    @Test fun a_fade_over_ten_seconds_is_refused_with_a_message() = runTest(dispatcher) {
        val vm = withPip(); vm.setPipFades(10_001, 0)
        assertEquals(EditorMessage.EditFailed(io.bsoft.bconverter.domain.timeline.EditError.InvalidFade), vm.message); assertEquals(0L, vm.pips.single().fadeInMs)
    }
    @Test fun trimming_the_selected_clip_trims_the_picture_in_picture_clip() = runTest(dispatcher) {
        val vm = withPip()
        vm.trimSelected(1_000, 3_000)
        assertEquals(2_000L, vm.pips.single().durationMs); assertEquals(12_345L, vm.timeline.videoClips.single().sourceEndMs)
    }
    @Test fun a_picture_in_picture_clip_can_be_moved_and_deleted() = runTest(dispatcher) {
        val vm = withPip(); val id = vm.pips.single().id
        vm.movePip(id, 5_000); assertEquals(5_000L, vm.pips.single().startTimeMs)
        vm.deleteSelected(); assertTrue(vm.pips.isEmpty())
        vm.undo(); assertEquals(5_000L, vm.pips.single().startTimeMs)
    }
    @Test fun the_last_added_clip_can_be_brought_to_the_front() = runTest(dispatcher) {
        val vm = withPip(); val first = vm.pips.single().id
        vm.seekTo(1_000); vm.onPipPicked(uri2); runCurrent()
        vm.select(first); vm.bringPipToFront()
        assertEquals(first, vm.pips.last().id)
    }

    // ================= canvas =================
    private val tall = io.bsoft.bconverter.domain.canvas.CanvasSettings(io.bsoft.bconverter.domain.canvas.CanvasAspect.TALL, background = io.bsoft.bconverter.domain.canvas.CanvasBackground.Blur)

    @Test fun the_canvas_starts_as_the_default() = runTest(dispatcher) {
        assertEquals(io.bsoft.bconverter.domain.canvas.CanvasSettings(), withVideo().state.value.canvas)
    }
    @Test fun a_tall_canvas_changes_the_frame_shape_of_the_preview() = runTest(dispatcher) {
        val vm = withVideo(); val before = vm.state.value.frameAspect
        vm.setCanvas(tall)
        assertEquals(tall, vm.state.value.canvas); assertEquals(9f / 16f, vm.state.value.frameAspect, 0.01f)
        assertTrue(before > 1f)
    }
    @Test fun a_canvas_change_can_be_undone_and_redone_in_order_with_other_edits() = runTest(dispatcher) {
        val vm = withVideo(); vm.setCanvas(tall); vm.select("id-1"); vm.setSpeed(2f)
        vm.undo(); assertEquals(tall, vm.state.value.canvas)
        vm.undo(); assertEquals(io.bsoft.bconverter.domain.canvas.CanvasSettings(), vm.state.value.canvas)
        vm.redo(); assertEquals(tall, vm.state.value.canvas)
    }
    @Test fun undoing_the_canvas_restores_the_frame_shape() = runTest(dispatcher) {
        val vm = withVideo(); vm.setCanvas(tall); vm.undo()
        assertTrue(vm.state.value.frameAspect > 1f)
    }
    @Test fun setting_the_same_canvas_again_is_not_an_undo_step() = runTest(dispatcher) {
        val vm = withVideo(); vm.setCanvas(tall); vm.setCanvas(tall); vm.undo()
        assertEquals(io.bsoft.bconverter.domain.canvas.CanvasSettings(), vm.state.value.canvas)
    }

    // ================= made-for presets =================
    @Test fun a_made_for_preset_sets_shape_size_and_quality_and_keeps_fit_and_background() = runTest(dispatcher) {
        val vm = withVideo(); vm.setCanvas(tall.copy(aspect = io.bsoft.bconverter.domain.canvas.CanvasAspect.AUTO, fit = io.bsoft.bconverter.domain.canvas.CanvasFit.FILL))
        vm.applySharePreset(io.bsoft.bconverter.domain.model.SharePreset.WHATSAPP_STATUS)
        val s = vm.state.value
        assertEquals(io.bsoft.bconverter.domain.canvas.CanvasAspect.TALL, s.canvas.aspect)
        assertEquals(io.bsoft.bconverter.domain.canvas.CanvasFit.FILL, s.canvas.fit); assertEquals(io.bsoft.bconverter.domain.canvas.CanvasBackground.Blur, s.canvas.background)
        assertEquals(io.bsoft.bconverter.domain.model.ExportSize.P720, s.exportSize); assertEquals(ExportPreset.SMALL_FILE, s.exportPreset)
    }
    @Test fun a_made_for_preset_changes_the_preview_shape_and_undo_restores_it_but_not_the_export_choices() = runTest(dispatcher) {
        val vm = withVideo(); vm.applySharePreset(io.bsoft.bconverter.domain.model.SharePreset.SQUARE)
        assertEquals(1f, vm.state.value.frameAspect, 0.01f)
        vm.undo()
        assertTrue(vm.state.value.frameAspect > 1f)
        assertEquals(io.bsoft.bconverter.domain.model.ExportSize.P1080, vm.state.value.exportSize)
    }
    @Test fun choosing_a_preset_for_the_shape_the_project_already_has_still_sets_size_and_quality() = runTest(dispatcher) {
        val vm = withVideo(); vm.applySharePreset(io.bsoft.bconverter.domain.model.SharePreset.YOUTUBE); vm.applySharePreset(io.bsoft.bconverter.domain.model.SharePreset.YOUTUBE)
        assertEquals(io.bsoft.bconverter.domain.model.ExportSize.P1080, vm.state.value.exportSize)
        vm.undo(); assertEquals(io.bsoft.bconverter.domain.canvas.CanvasAspect.AUTO, vm.state.value.canvas.aspect)
    }

    @Test fun export_quality_and_size_are_saved_with_the_project_and_restored_when_it_is_reopened() = runTest(dispatcher) {
        val vm = withVideo(); vm.onExportPresetSelected(ExportPreset.HIGH_QUALITY); vm.onExportSizeSelected(io.bsoft.bconverter.domain.model.ExportSize.P480); runCurrent()
        val choices = io.bsoft.bconverter.domain.model.ExportChoices(ExportPreset.HIGH_QUALITY, io.bsoft.bconverter.domain.model.ExportSize.P480)
        assertEquals(choices, vm.saved!!.project.exportChoices)
        val reopened = vm(); reopened.openProject(vm.state.value.projectId); runCurrent()
        assertEquals(ExportPreset.HIGH_QUALITY, reopened.state.value.exportPreset); assertEquals(io.bsoft.bconverter.domain.model.ExportSize.P480, reopened.state.value.exportSize)
    }
    @Test fun a_made_for_preset_is_saved_too() = runTest(dispatcher) {
        val vm = withVideo(); vm.applySharePreset(io.bsoft.bconverter.domain.model.SharePreset.WHATSAPP_STATUS); runCurrent()
        assertEquals(io.bsoft.bconverter.domain.model.ExportChoices(ExportPreset.SMALL_FILE, io.bsoft.bconverter.domain.model.ExportSize.P720), vm.saved!!.project.exportChoices)
        assertEquals(io.bsoft.bconverter.domain.canvas.CanvasAspect.TALL, vm.saved!!.project.canvas.aspect)
    }

    // ================= image overlays =================
    @Test fun a_picked_picture_becomes_a_selected_overlay_at_the_playhead() = runTest(dispatcher) {
        val vm = withVideo()
        vm.onOverlayImagePicked(uri2); runCurrent()
        val o = vm.state.value.textOverlays.single()
        assertTrue(o.isImage); assertEquals(o.id, vm.state.value.selectedOverlayId)
        assertEquals(listOf(uri2.toString()), imageImporter.requests.map { it.first })
    }
    @Test fun a_picture_joins_the_same_layer_order_as_text() = runTest(dispatcher) {
        val vm = withVideo(); vm.addTextOverlay(0); vm.onOverlayImagePicked(uri2); runCurrent()
        assertEquals(listOf(false, true), vm.state.value.textOverlays.map { it.isImage })
    }
    @Test fun a_picture_can_be_undone() = runTest(dispatcher) {
        val vm = withVideo(); vm.onOverlayImagePicked(uri2); runCurrent()
        vm.undo(); assertTrue(vm.state.value.textOverlays.isEmpty())
        vm.redo(); assertTrue(vm.state.value.textOverlays.single().isImage)
    }
    @Test fun an_unreadable_picture_changes_nothing_and_says_so() = runTest(dispatcher) {
        val vm = withVideo(); imageImporter.result = io.bsoft.bconverter.domain.overlay.ImageImport.Unreadable
        vm.onOverlayImagePicked(uri2); runCurrent()
        assertTrue(vm.state.value.textOverlays.isEmpty()); assertEquals(EditorMessage.ImageUnreadable, vm.message)
    }
    @Test fun a_huge_picture_says_it_is_too_large() = runTest(dispatcher) {
        val vm = withVideo(); imageImporter.result = io.bsoft.bconverter.domain.overlay.ImageImport.TooLarge
        vm.onOverlayImagePicked(uri2); runCurrent()
        assertEquals(EditorMessage.ImageTooLarge, vm.message)
    }
    @Test fun a_dismissed_image_picker_changes_nothing() = runTest(dispatcher) {
        val vm = withVideo(); vm.onOverlayImagePicked(null); runCurrent()
        assertTrue(vm.state.value.textOverlays.isEmpty()); assertTrue(imageImporter.requests.isEmpty()); assertNull(vm.message)
    }
    @Test fun the_picture_is_sized_for_the_video_frame() = runTest(dispatcher) {
        val vm = withVideo(); vm.onOverlayImagePicked(uri2); runCurrent()
        assertTrue(imageImporter.requests.single().second > 0f) // the video's own width / height
    }

    // ================= implementation plan, steps 9, 11 and 12 =================
    @Test fun a_pick_made_while_another_file_is_importing_is_queued_not_dropped() = runTest(dispatcher) {
        resolver.gate = CompletableDeferred()
        val vm = vm(); vm.onVideoPicked(uri1); runCurrent(); vm.onVideoPicked(uri2); runCurrent()
        assertTrue(vm.state.value.busy)
        resolver.gate!!.complete(Unit); runCurrent()
        assertEquals(2, vm.timeline.videoClips.size); assertFalse(vm.state.value.busy)
    }

    @Test fun after_process_death_the_stored_project_is_reopened() = runTest(dispatcher) {
        val saved = androidx.lifecycle.SavedStateHandle()
        val before = vm(saved).also { it.onVideoPicked(uri1); runCurrent() }
        val id = before.state.value.projectId
        val after = vm(saved); runCurrent()
        assertEquals(id, after.state.value.projectId); assertEquals(1, after.timeline.videoClips.size)
    }

    @Test fun an_untouched_new_project_is_not_reopened_after_process_death() = runTest(dispatcher) {
        val saved = androidx.lifecycle.SavedStateHandle()
        vm(saved); runCurrent()
        val after = vm(saved); runCurrent()
        assertTrue(after.timeline.videoClips.isEmpty()); assertNull(after.message)
    }

    @Test fun an_export_destination_delivered_after_process_death_still_exports() = runTest(dispatcher) {
        val saved = androidx.lifecycle.SavedStateHandle()
        vm(saved).also { it.onVideoPicked(uri1); runCurrent(); it.onExportRequested(); runCurrent() }
        val revived = vm(saved); revived.onExportDestinationChosen(destination); runCurrent()
        assertEquals(1, engine.rendered.size); assertEquals(ExportUiState.Completed, revived.state.value.export)
    }

    @Test fun a_new_project_takes_the_localised_default_name_but_a_renamed_one_keeps_its_name() = runTest(dispatcher) {
        val fresh = vm(); fresh.applyDefaultName("Projet sans titre")
        assertEquals("Projet sans titre", fresh.state.value.projectName)
        val renamed = vm(); renamed.renameProject("Holiday"); renamed.applyDefaultName("Projet sans titre")
        assertEquals("Holiday", renamed.state.value.projectName)
    }

    @Test fun a_new_subtitle_track_is_named_by_the_screen() = runTest(dispatcher) {
        val vm = vm(); vm.addEmptySubtitleTrack { "Sous-titres $it" }
        assertEquals("Sous-titres 1", vm.state.value.subtitleTracks.single().name)
    }

    // ================= step 22: several clips from the Photo Picker =================
    @Test fun clips_picked_together_are_appended_in_the_order_picked() = runTest(dispatcher) {
        resolver.gate = CompletableDeferred()
        val vm = vm(); vm.onVideosPicked(listOf(uri2, uri1)); runCurrent()
        resolver.gate!!.complete(Unit); runCurrent()
        assertEquals(listOf(MediaSource.UriSource(uri2), MediaSource.UriSource(uri1)), vm.timeline.videoClips.map { it.source })
    }
}
