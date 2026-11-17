package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipDragTest {
    @Test fun an_audio_clip_moves_by_the_dragged_time_but_never_before_zero() {
        assertEquals(7_000L, ClipDrag.audioStart(startMs = 5_000, deltaMs = 2_000))
        assertEquals(0L, ClipDrag.audioStart(startMs = 5_000, deltaMs = -9_000))
        assertEquals(65_000L, ClipDrag.audioStart(startMs = 5_000, deltaMs = 60_000))
    }

    @Test fun the_lane_follows_the_vertical_drag_and_stays_inside_the_lanes() {
        assertEquals(1, ClipDrag.audioLane(lane = 0, laneCount = 3, deltaPx = 70f, laneStepPx = 50f))
        assertEquals(0, ClipDrag.audioLane(lane = 0, laneCount = 3, deltaPx = 20f, laneStepPx = 50f))
        assertEquals(2, ClipDrag.audioLane(lane = 1, laneCount = 3, deltaPx = 500f, laneStepPx = 50f))
        assertEquals(0, ClipDrag.audioLane(lane = 1, laneCount = 3, deltaPx = -500f, laneStepPx = 50f))
    }

    private val t = TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000))

    @Test fun a_video_clip_dropped_past_the_middle_of_a_neighbour_takes_its_place() {
        assertEquals(1, ClipDrag.videoIndex(t, "a", deltaMs = 11_000))
        assertEquals(0, ClipDrag.videoIndex(t, "a", deltaMs = 4_000))
        assertEquals(2, ClipDrag.videoIndex(t, "a", deltaMs = 25_000))
        assertEquals(0, ClipDrag.videoIndex(t, "c", deltaMs = -25_000))
        assertEquals(1, ClipDrag.videoIndex(t, "c", deltaMs = -11_000))
    }

    @Test fun a_clip_left_where_it_was_keeps_its_index() {
        assertEquals(1, ClipDrag.videoIndex(t, "b", deltaMs = 0))
        assertEquals(1, ClipDrag.videoIndex(t, "b", deltaMs = 3_000))
        assertEquals(1, ClipDrag.videoIndex(t, "b", deltaMs = -3_000))
    }
}
