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
class ProjectSerializerSubtitleTest {
    private val style = SubtitleStyle(
        name = "Top", fontFamily = "Roboto", fontSize = 36.5f, bold = true, italic = true, underline = true,
        primaryColor = 0xFFFFCC00.toInt(), outlineColor = 0xFF112233.toInt(), backColor = 0x80000000.toInt(), boxed = true,
        outlineWidth = 3.5f, shadow = 0.5f, alignment = SubtitleAlignment.TOP_CENTER, marginL = 11, marginR = 12, marginV = 13,
    )
    private val track = SubtitleTrack(
        "t1", "English", listOf(SubtitleCue("c1", 1000, 2500, "Hello <i>there</i>\nworld", "Top", "\\an8\\pos(100,50)"), SubtitleCue("c2", 3000, 4000, "Bye")),
        mapOf("Top" to style), language = "eng", title = "Full", offsetMs = -250, sourceFormat = SubtitleFormat.ASS,
        playResX = 1280, playResY = 720, isDefault = true, isForced = true,
    )

    private fun stored(vararg tracks: SubtitleTrack) =
        StoredProject(VideoProject("p-1", "P", Timeline(emptyList(), emptyList()), TL.exportSettings(), subtitleTracks = tracks.toList()), 1, 2)
    private fun roundTrip(s: StoredProject) = (ProjectSerializer.decode(ProjectSerializer.encode(s)) as DecodeResult.Decoded).stored

    @Test fun a_full_track_round_trips_unchanged() = assertEquals(listOf(track), roundTrip(stored(track)).project.subtitleTracks)

    @Test fun a_minimal_track_round_trips_unchanged() {
        val minimal = SubtitleTrack("t2", "Plain")
        assertEquals(listOf(minimal), roundTrip(stored(minimal)).project.subtitleTracks)
    }

    @Test fun track_order_is_kept() = assertEquals(listOf("t2", "t1"), roundTrip(stored(SubtitleTrack("t2", "B"), track)).project.subtitleTracks.map { it.id })

    @Test fun projects_saved_before_subtitles_existed_open_with_no_tracks() {
        val json = ProjectSerializer.encode(stored())
        val old = org.json.JSONObject(json).also { it.remove("subtitleTracks") }.toString()
        assertEquals(emptyList<SubtitleTrack>(), (ProjectSerializer.decode(old) as DecodeResult.Decoded).stored.project.subtitleTracks)
    }

    @Test fun a_project_without_tracks_does_not_write_the_field() = assertFalse(ProjectSerializer.encode(stored()).contains("subtitleTracks"))

    @Test fun a_track_with_an_invalid_cue_makes_the_project_corrupt() {
        val bad = ProjectSerializer.encode(stored(track)).replace("\"endMs\":2500", "\"endMs\":500")
        assertTrue(ProjectSerializer.decode(bad) is DecodeResult.Corrupt)
    }

    @Test fun an_unknown_alignment_makes_the_project_corrupt() {
        val bad = ProjectSerializer.encode(stored(track)).replace("\"alignment\":8", "\"alignment\":42")
        assertTrue(ProjectSerializer.decode(bad) is DecodeResult.Corrupt)
    }
}
