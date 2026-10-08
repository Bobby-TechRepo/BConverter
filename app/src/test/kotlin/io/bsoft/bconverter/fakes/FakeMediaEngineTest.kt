package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.model.*
import io.bsoft.bconverter.media.api.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeMediaEngineTest {
    private val source = MediaSource.FileSource(File("in.mp4"))

    @Test fun probe_returns_configured_metadata_and_records_source() = runTest {
        val engine = FakeMediaEngine()
        engine.probeResult = MediaMetadata(5000, ContainerFormat.MP4)
        assertEquals(5000, engine.probe(source).durationMs)
        assertEquals(listOf<MediaSource>(source), engine.probedSources)
    }

    @Test fun observeProgress_delivers_emitted_progress_for_its_job() = runTest {
        val engine = FakeMediaEngine()
        val p = MediaProcessingProgress(1000, 4000, 0.25f, 1.0, 3000)
        engine.emitProgress("job-1", p)
        assertEquals(p, engine.observeProgress("job-1").first())
    }

    @Test fun cancel_records_job_id() = runTest {
        val engine = FakeMediaEngine()
        engine.cancel("job-9")
        assertEquals(listOf("job-9"), engine.cancelledJobIds)
    }
}
