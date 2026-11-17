package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.model.ExportChoices
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.model.ExportSize
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerExportChoicesTest {
    private fun stored(c: ExportChoices) =
        StoredProject(VideoProject("p", "P", TL.timeline(TL.video("a", 5_000)), TL.exportSettings(), exportChoices = c), 1, 2)
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project

    @Test fun the_choices_are_kept() {
        val c = ExportChoices(ExportPreset.SMALL_FILE, ExportSize.P720)
        assertEquals(c, decode(ProjectSerializer.encode(stored(c))).exportChoices)
    }
    @Test fun the_defaults_write_nothing_and_old_projects_read_as_the_defaults() {
        val text = ProjectSerializer.encode(stored(ExportChoices()))
        assertFalse(text.contains("exportChoices"))
        assertEquals(ExportChoices(), decode(text).exportChoices)
    }
    @Test fun an_unknown_name_falls_back_for_that_choice_only() {
        val text = ProjectSerializer.encode(stored(ExportChoices(ExportPreset.HIGH_QUALITY, ExportSize.P480)))
        val back = decode(text.replace("P480", "P9000")).exportChoices
        assertEquals(ExportChoices(ExportPreset.HIGH_QUALITY, ExportSize.AUTO), back)
    }
}
