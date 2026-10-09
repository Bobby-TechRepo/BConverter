package io.bsoft.bconverter.data.media

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaProberTest {
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val source = MediaSource.FileSource(File("/tmp/a.mp4"))
    private val mp4Json = checkNotNull(javaClass.getResource("/ffprobe/mp4_h264_aac.json")).readText()
    private fun prober() = MediaProber(resolver, runner)
    private val staged get() = (resolver.result as ResolveResult.Success).input

    @Test fun success_returns_metadata_and_keeps_the_input_for_the_caller() = runTest {
        runner.result = FfprobeRunResult.Success(mp4Json)
        val outcome = prober().probe(source) as ProbeOutcome.Success
        assertEquals(ContainerFormat.MP4, outcome.metadata.container)
        assertEquals(staged, outcome.input)
        assertEquals(listOf(staged.file), runner.files)
        assertTrue(resolver.released.isEmpty())
    }

    @Test fun input_failure_is_reported_and_ffprobe_is_never_run() = runTest {
        resolver.result = ResolveResult.Failure(InputError.PermissionDenied)
        assertEquals(
            ProbeOutcome.Failure(ProbeFailure.Input(InputError.PermissionDenied)),
            prober().probe(source),
        )
        assertTrue(runner.files.isEmpty())
    }

    @Test fun tool_failure_is_reported_and_staged_input_released() = runTest {
        runner.result = FfprobeRunResult.Failure(exitCode = 1)
        assertEquals(ProbeOutcome.Failure(ProbeFailure.ToolFailed(1)), prober().probe(source))
        assertEquals(listOf(staged), resolver.released)
    }

    @Test fun unparsable_output_is_invalid_and_staged_input_released() = runTest {
        runner.result = FfprobeRunResult.Success("garbage")
        assertEquals(ProbeOutcome.Failure(ProbeFailure.InvalidOutput), prober().probe(source))
        assertEquals(listOf(staged), resolver.released)
    }

    @Test fun file_without_media_streams_is_reported_and_input_released() = runTest {
        runner.result = FfprobeRunResult.Success("""{"streams":[],"format":{"format_name":"mp4"}}""")
        assertEquals(ProbeOutcome.Failure(ProbeFailure.NoMediaStreams), prober().probe(source))
        assertEquals(listOf(staged), resolver.released)
    }

    @Test fun cancellation_releases_staged_input_and_propagates() = runTest {
        runner.throwOnRun = CancellationException("cancelled")
        try { prober().probe(source); fail("expected cancellation") } catch (_: CancellationException) {}
        assertEquals(listOf(staged), resolver.released)
    }
}
