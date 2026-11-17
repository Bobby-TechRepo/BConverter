package io.bsoft.bconverter.flows

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.project.ProjectOpener
import io.bsoft.bconverter.domain.project.TimeSource
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
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
import io.bsoft.bconverter.fakes.FakeUriPermissionPersister
import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.feature.editor.EditorActions
import io.bsoft.bconverter.feature.editor.EditorContent
import io.bsoft.bconverter.feature.editor.EditorEvent
import io.bsoft.bconverter.feature.editor.EditorLayoutMode
import io.bsoft.bconverter.feature.editor.EditorViewModel
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import io.bsoft.bconverter.media.player.TimelinePlaybackState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Spec §42, editor flow: open editor → add video → split → trim → delete → add audio → change speed → undo → redo → export.
 * The real ViewModel and screen run against fake media tools. Playhead position is set through the ViewModel (the same call the
 * timeline's tap-to-seek makes) because tap geometry is covered by EditorContentTest.
 */
class EditorFlowTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)

    private val dir = File(context.cacheDir, "flow-editor").apply { deleteRecursively(); mkdirs() }
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val engine = FakeMediaEngine()
    private val publisher = FakeOutputPublisher()
    private val registry = ExportRegistry()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val projects = FakeProjectRepository()
    private val videoUri: Uri = Uri.parse("content://flow/video")
    private val audioUri: Uri = Uri.parse("content://flow/audio")
    private val destination: Uri = Uri.parse("content://flow/export")
    private lateinit var vm: EditorViewModel

    @Before fun setUp() {
        val staging = StagingDirectory(File(dir, "staging"))
        val video = ResolvedInput(File(dir, "video.mp4").apply { writeText("v") }, "video.mp4", 7_340_032, true)
        val audio = ResolvedInput(File(dir, "music.mp4").apply { writeText("a") }, "music.mp4", 1_000_000, true)
        resolver.resultFor = { src -> ResolveResult.Success(if ((src as MediaSource.UriSource).uri == videoUri) video else audio) }
        runner.result = FfprobeRunResult.Success(checkNotNull(javaClass.getResource("/ffprobe/mp4_h264_aac.json")).readText())
        engine.onRender = { _, out -> out.file.writeText("rendered") }
        val launcher = ExportJobLauncher(registry, DirectExportScheduler(scope, ExportJobExecutor(engine, resolver, publisher, registry)))
        val ids = SequentialIds()
        vm = EditorViewModel(
            MediaProber(resolver, runner), resolver, FakeCapabilitySource(),
            ProjectExporter(staging, StorageGuard(FakeFreeSpaceProvider(), staging), launcher, ids),
            launcher, FakeUriPermissionPersister(), ids, projects, ProjectOpener(projects) { true }, TimeSource { 1_000L },
            io.bsoft.bconverter.domain.usecase.SubtitleImporter(io.bsoft.bconverter.fakes.FakeContentReader(), ids),
            io.bsoft.bconverter.fakes.FakeOverlayImageImporter(),
        )
    }

    @After fun tearDown() { scope.cancel(); dir.deleteRecursively() }

    private fun tap(id: Int) { rule.onNodeWithText(s(id)).performScrollTo().performClick() }
    private fun clip(index: Int, of: Int) =
        rule.onNode(hasContentDescription(s(R.string.editor_clip_video, index, of, ""), substring = true))
    private val videoClips get() = vm.state.value.editor.timeline.videoClips
    private val audioClips get() = vm.state.value.editor.timeline.audioTracks.flatMap { it.clips }
    private fun waitFor(condition: () -> Boolean) = rule.waitUntil(5_000) { condition() }

    @Test fun a_project_is_edited_from_import_to_export() {
        rule.setContent {
            val state by vm.state.collectAsState()
            LaunchedEffect(vm) { vm.events.collect { if (it is EditorEvent.RequestExportDestination) vm.onExportDestinationChosen(destination) } }
            EditorContent(
                state = state,
                playback = TimelinePlaybackState(),
                playheadMs = state.editor.playheadMs,
                layout = EditorLayoutMode.PHONE,
                actions = EditorActions(
                    onAddVideo = { vm.onVideoPicked(videoUri) },
                    onAddAudio = { vm.onAudioPicked(audioUri) },
                    onSplit = vm::splitAtPlayhead, onDelete = vm::deleteSelected, onTrim = vm::trimSelected,
                    onSpeed = vm::setSpeed, onUndo = vm::undo, onRedo = vm::redo, onSelect = vm::select, onSeek = vm::seekTo,
                    onExportStart = vm::onExportRequested,
                ),
                preview = { Box(it) },
            )
        }

        // Add video.
        rule.onNodeWithText(s(R.string.editor_empty_timeline)).assertIsDisplayed()
        tap(R.string.editor_add_video)
        waitFor { videoClips.size == 1 }
        assertEquals(12_345L, videoClips.single().sourceEndMs)

        // Select the clip, then split it at 4 s (splitting works on the selected clip).
        clip(1, 1).performScrollTo().performClick()
        waitFor { vm.state.value.editor.selectedClipId == videoClips[0].id }
        rule.runOnUiThread { vm.seekTo(4_000) }
        tap(R.string.editor_split)
        waitFor { videoClips.size == 2 }
        assertEquals(videoClips[0].sourceEndMs, videoClips[1].sourceStartMs)

        // Trim the first clip in its panel: move the start handle to the right (applied when let go), then close the panel.
        clip(1, 2).performScrollTo().performClick()
        waitFor { vm.state.value.editor.selectedClipId == videoClips[0].id }
        tap(R.string.editor_trim)
        rule.onNodeWithText(s(R.string.editor_trim_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription("${s(R.string.editor_trim_start)}, ${s(R.string.editor_trim_end)}").performTouchInput {
            // A tap on the track moves the nearest handle there; 10% is nearer the start handle (at 0) than the end handle (at ~32%).
            click(Offset(width * 0.1f, centerY))
        }
        rule.onNodeWithText(s(R.string.editor_panel_done)).performClick()
        waitFor { videoClips[0].sourceStartMs > 0 }

        // Delete the second clip.
        clip(2, 2).performScrollTo().performClick()
        waitFor { vm.state.value.editor.selectedClipId == videoClips[1].id }
        tap(R.string.editor_delete)
        waitFor { videoClips.size == 1 }

        // Add audio.
        tap(R.string.editor_add_audio)
        waitFor { audioClips.size == 1 }

        // Change speed of the remaining video clip.
        clip(1, 1).performScrollTo().performClick()
        waitFor { vm.state.value.editor.selectedClipId == videoClips[0].id }
        tap(R.string.editor_speed)
        rule.onNodeWithText(s(R.string.editor_clip_speed, "2")).performClick()
        waitFor { videoClips[0].playbackSpeed == 2f }

        // Undo and redo.
        tap(R.string.editor_undo)
        waitFor { videoClips[0].playbackSpeed == 1f }
        tap(R.string.editor_redo)
        waitFor { videoClips[0].playbackSpeed == 2f }

        // Export: the dialog starts it, the (simulated) save dialog answers, and completion is displayed.
        rule.onNodeWithText(s(R.string.editor_export)).performClick() // in the top bar, outside the scrolling tools
        rule.onNodeWithText(s(R.string.editor_export_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.editor_export_start)).performClick()
        waitFor { publisher.published.isNotEmpty() }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText(s(R.string.editor_export_done)).assertIsDisplayed() }.isSuccess }
        assertEquals(destination, publisher.published.single().destination)
        assertTrue(engine.rendered.single().first.timeline.audioTracks.flatMap { it.clips }.size == 1)
    }
}
