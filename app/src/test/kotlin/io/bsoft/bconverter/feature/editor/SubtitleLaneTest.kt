package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleLaneTest {
    private val scale = TimelineScale(100f) // 100 px per second
    private val track = SubtitleTrack("t", "T", listOf(SubtitleCue("a", 1000, 2000, "A"), SubtitleCue("b", 3000, 3004, "B")))

    @Test fun cues_are_placed_by_time_with_a_minimum_width() {
        val lane = scale.subtitleLane(track)
        assertEquals(listOf("a", "b"), lane.map { it.id })
        assertEquals(100f, lane[0].xPx, 0.01f); assertEquals(100f, lane[0].widthPx, 0.01f)
        assertEquals(300f, lane[1].xPx, 0.01f); assertEquals(TimelineScale.MIN_BLOCK_WIDTH_PX, lane[1].widthPx, 0.01f)
    }

    @Test fun the_track_offset_moves_the_blocks() {
        assertEquals(50f, scale.subtitleLane(track.copy(offsetMs = -500)).first().xPx, 0.01f)
    }

    @Test fun the_lane_reaches_the_end_of_the_last_cue() = assertEquals(3004L, SubtitleTimelineExtent.endMs(listOf(track)))

    @Test fun no_tracks_have_no_extent() = assertEquals(0L, SubtitleTimelineExtent.endMs(emptyList()))
}
