package io.bsoft.bconverter.domain.model

import android.net.Uri
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportJobModelTest {
    private val dest: Uri = Uri.parse("content://d/1")
    private val in1 = ResolvedInput(File("/tmp/in1"), "in1", 1, true)
    private val in2 = ResolvedInput(File("/tmp/in2"), "in2", 1, true)
    private val out = File("/tmp/out")

    @Test fun a_conversion_has_one_input_and_its_output_file() {
        val job = ExportJob("j", "t", ExportTask.Convert(ConversionRequest("j", in1, io.bsoft.bconverter.fakes.TestMedia.metadata(), out,
            ConversionSettings(ContainerFormat.MP4, VideoPlan.Copy, AudioPlan.Copy))), dest)
        assertEquals(listOf(in1), job.inputs); assertEquals(out, job.outputFile)
    }

    @Test fun an_extraction_has_one_input_and_its_output_file() {
        val job = ExportJob("j", "t", ExportTask.Extract(AudioExtractionRequest("j", in1, io.bsoft.bconverter.fakes.TestMedia.metadata(), out,
            AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy))), dest)
        assertEquals(listOf(in1), job.inputs); assertEquals(out, job.outputFile)
    }

    @Test fun a_project_render_owns_every_staged_input_and_has_its_output_file() {
        val job = ExportJob("j", "t", ExportTask.Render(TL.project(TL.timeline(TL.video("a"))), out, listOf(in1, in2)), dest)
        assertEquals(listOf(in1, in2), job.inputs); assertEquals(out, job.outputFile)
    }
}
