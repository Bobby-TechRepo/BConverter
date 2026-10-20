package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceLibraryTest {
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val library = SourceLibrary(MediaProber(resolver, runner), resolver)
    private val uri1: Uri = Uri.parse("content://x/1")
    private val uri2: Uri = Uri.parse("content://x/2")
    private val staged1 = ResolvedInput(File("/tmp/one.mp4"), "one.mp4", 100, true)
    private val staged2 = ResolvedInput(File("/tmp/two.mp4"), "two.mp4", 200, true)

    @Before fun setUp() {
        runner.result = FfprobeRunResult.Success(checkNotNull(javaClass.getResource("/ffprobe/mp4_h264_aac.json")).readText())
        resolver.resultFor = { src -> ResolveResult.Success(if ((src as MediaSource.UriSource).uri == uri1) staged1 else staged2) }
    }

    @Test fun adding_a_file_stages_and_probes_it() = runTest {
        val added = library.add(uri1) as SourceLibrary.AddResult.Added
        assertEquals(MediaSource.UriSource(uri1), added.source); assertEquals("one.mp4", added.displayName)
        assertEquals(12_345L, added.metadata.durationMs); assertTrue(added.newlyStaged)
        assertEquals(listOf(staged1), library.inputs()); assertEquals(12_345L, library.metadata(added.source)!!.durationMs)
    }

    @Test fun adding_the_same_file_again_reuses_the_staged_copy() = runTest {
        library.add(uri1)
        val again = library.add(uri1) as SourceLibrary.AddResult.Added
        assertEquals(false, again.newlyStaged); assertEquals(1, resolver.resolved.size); assertEquals(listOf(staged1), library.inputs())
    }

    @Test fun a_failed_probe_is_reported_and_nothing_is_kept() = runTest {
        runner.result = FfprobeRunResult.Failure(1)
        assertEquals(SourceLibrary.AddResult.Failed(ProbeFailure.ToolFailed(1)), library.add(uri1))
        assertTrue(library.inputs().isEmpty())
    }

    @Test fun an_unreadable_file_is_reported_as_an_input_problem() = runTest {
        resolver.resultFor = { ResolveResult.Failure(InputError.NotFound) }
        assertEquals(SourceLibrary.AddResult.Failed(ProbeFailure.Input(InputError.NotFound)), library.add(uri1))
    }

    @Test fun discarding_releases_the_staged_copy_and_forgets_the_source() = runTest {
        val added = library.add(uri1) as SourceLibrary.AddResult.Added
        library.discard(added.source)
        assertEquals(listOf(staged1), resolver.released); assertTrue(library.inputs().isEmpty()); assertNull(library.metadata(added.source))
    }

    // ---- rendering view ----
    private fun project(source: MediaSource) = VideoProject(
        "p", "P", Timeline(listOf(VideoClip("v", source, 12_345, 0, 12_345))).normalized(), TL.exportSettings(),
    )

    @Test fun for_rendering_uri_sources_become_staged_files_in_the_timeline_and_the_metadata() = runTest {
        val added = library.add(uri1) as SourceLibrary.AddResult.Added
        val rendered = library.forRender(project(added.source))
        assertEquals(MediaSource.FileSource(staged1.file), rendered.timeline.videoClips.single().source)
        assertEquals(setOf<MediaSource>(MediaSource.FileSource(staged1.file)), rendered.sourceMetadata.keys)
    }

    @Test fun sources_that_are_not_staged_are_left_alone_so_the_planner_can_report_them() = runTest {
        val unknown = MediaSource.UriSource(uri2)
        assertEquals(unknown, library.forRender(project(unknown)).timeline.videoClips.single().source)
    }

    // ---- release ----
    @Test fun releasing_everything_frees_every_staged_copy() = runTest {
        library.add(uri1); library.add(uri2)
        library.releaseAll()
        assertEquals(listOf(staged1, staged2), resolver.released); assertTrue(library.inputs().isEmpty())
    }

    @Test fun releasing_all_except_some_keeps_those_for_their_new_owner() = runTest {
        library.add(uri1); library.add(uri2)
        library.releaseAllExcept(listOf(staged1))
        assertEquals(listOf(staged2), resolver.released); assertTrue(library.inputs().isEmpty())
    }
}
