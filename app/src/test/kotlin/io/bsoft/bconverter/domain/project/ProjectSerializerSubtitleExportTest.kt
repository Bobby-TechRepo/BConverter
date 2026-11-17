package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.subtitle.SubtitleAlignment
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleFormat
import io.bsoft.bconverter.domain.subtitle.SubtitleStyle
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerSubtitleExportTest {
    private fun roundTrip(t: SubtitleTrack) = (ProjectSerializer.decode(ProjectSerializer.encode(
        StoredProject(VideoProject("p", "P", Timeline(emptyList(), emptyList()), TL.exportSettings(), subtitleTracks = listOf(t)), 1, 2))) as DecodeResult.Decoded).stored.project.subtitleTracks.single()

    @Test fun every_export_mode_round_trips() = io.bsoft.bconverter.domain.subtitle.SubtitleExportMode.entries.forEach {
        assertEquals(it, roundTrip(SubtitleTrack("t", "T", exportMode = it)).exportMode)
    }

    @Test fun tracks_saved_before_export_modes_existed_are_exported_as_soft_subtitles() {
        val json = ProjectSerializer.encode(StoredProject(VideoProject("p", "P", Timeline(emptyList(), emptyList()), TL.exportSettings(), subtitleTracks = listOf(SubtitleTrack("t", "T"))), 1, 2))
        assertFalse(json.contains("exportMode")) // soft is the default and is not written
        assertEquals(io.bsoft.bconverter.domain.subtitle.SubtitleExportMode.SOFT, roundTrip(SubtitleTrack("t", "T")).exportMode)
    }

    @Test fun an_unknown_mode_makes_the_project_corrupt() {
        val bad = ProjectSerializer.encode(StoredProject(VideoProject("p", "P", Timeline(emptyList(), emptyList()), TL.exportSettings(), subtitleTracks = listOf(SubtitleTrack("t", "T", exportMode = io.bsoft.bconverter.domain.subtitle.SubtitleExportMode.BURNED))), 1, 2))
            .replace("BURNED", "WHATEVER")
        assertTrue(ProjectSerializer.decode(bad) is DecodeResult.Corrupt)
    }
}
