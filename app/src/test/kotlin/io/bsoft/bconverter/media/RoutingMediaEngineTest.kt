package io.bsoft.bconverter.media

import io.bsoft.bconverter.domain.render.HardwareRender
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.media.transformer.HardwareRenderer
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoutingMediaEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ffmpeg = FakeMediaEngine()
    private val plans = mutableListOf<HardwareRender.Plan>()
    private var hardwareResult: (File) -> MediaProcessingResult = { MediaProcessingResult.Success(it) }
    private val hardware = HardwareRenderer { plan, output, onProgress -> plans += plan; onProgress(0.5f); hardwareResult(output) }
    private val engine = RoutingMediaEngine(ffmpeg, hardware)
    private fun output() = RenderOutput(File(tmp.root, "out.mp4"))
    private val simple = TL.project(TL.timeline(TL.video("a", 4_000)))

    @Test fun an_eligible_project_is_rendered_on_the_device_encoders() = runTest {
        val out = output()
        assertEquals(MediaProcessingResult.Success(out.file), engine.render(simple, out))
        assertEquals(1, plans.size)
        assertTrue(ffmpeg.rendered.isEmpty())
    }

    @Test fun anything_else_goes_to_ffmpeg() = runTest {
        engine.render(simple.copy(export = simple.export.copy(container = io.bsoft.bconverter.domain.model.ContainerFormat.MKV)), output())
        assertTrue(plans.isEmpty())
        assertEquals(1, ffmpeg.rendered.size)
    }

    @Test fun a_failed_hardware_export_falls_back_to_ffmpeg_and_removes_the_partial_file() = runTest {
        hardwareResult = { it.writeText("partial"); MediaProcessingResult.Failure("encoder") }
        val out = output()
        engine.render(simple, out)
        assertEquals(1, ffmpeg.rendered.size)
        assertTrue(!out.file.exists())
    }

    @Test fun cancelling_stops_the_hardware_export_and_reports_it() = runTest {
        val gate = CompletableDeferred<MediaProcessingResult>()
        val gated = RoutingMediaEngine(ffmpeg, HardwareRenderer { _, _, _ -> gate.await() })
        val running = async { gated.render(simple, output()) }
        runCurrent()
        gated.cancel(simple.id)
        assertEquals(MediaProcessingResult.Cancelled, running.await())
        assertTrue(ffmpeg.rendered.isEmpty())
        assertEquals(listOf(simple.id), ffmpeg.cancelledJobIds)
    }

    @Test fun without_a_hardware_renderer_everything_goes_to_ffmpeg() = runTest {
        RoutingMediaEngine(ffmpeg, null).render(simple, output())
        assertEquals(1, ffmpeg.rendered.size)
    }
}
