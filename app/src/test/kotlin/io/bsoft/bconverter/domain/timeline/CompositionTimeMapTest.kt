package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionTimeMapTest {
    // 4 s of source at normal speed, then 4 s of source (from 10 s) at double speed = 2 s on screen.
    private val segments = listOf(
        PreviewSegment("a", TL.source("a"), 0, 4_000, 1f, 0, sourceDurationMs = 20_000),
        PreviewSegment("b", TL.source("b"), 10_000, 14_000, 2f, 4_000, sourceDurationMs = 20_000),
    )
    private val map = CompositionTimeMap(segments)

    @Test fun the_composition_lasts_as_long_as_the_segments_play() {
        assertEquals(6_000L, map.totalMs)
        assertEquals(2_000L, segments[1].playDurationMs)
    }

    @Test fun source_positions_map_to_composition_time_and_back() {
        assertEquals(1_500L, map.toCompositionMs(0, 1_500))
        assertEquals(5_000L, map.toCompositionMs(1, 12_000))
        assertEquals(0 to 1_500L, map.fromCompositionMs(1_500))
        assertEquals(1 to 12_000L, map.fromCompositionMs(5_000))
    }

    @Test fun a_boundary_belongs_to_the_later_segment_and_the_end_to_the_last() {
        assertEquals(1 to 10_000L, map.fromCompositionMs(4_000))
        assertEquals(1 to 14_000L, map.fromCompositionMs(6_000))
        assertEquals(1 to 14_000L, map.fromCompositionMs(99_000))
    }

    @Test fun positions_outside_a_segment_are_clamped() {
        assertEquals(4_000L, map.toCompositionMs(1, 9_000))
        assertEquals(0L, map.toCompositionMs(7, 0))
        assertEquals(0 to 0L, map.fromCompositionMs(-5))
    }

    @Test fun an_empty_track_has_no_positions() {
        assertNull(CompositionTimeMap(emptyList()).fromCompositionMs(0))
        assertEquals(0L, CompositionTimeMap(emptyList()).totalMs)
    }

    @Test fun preview_segments_carry_the_source_length() {
        val timeline = TL.timeline(TL.video("a", 4_000, sourceDuration = 30_000))
        assertEquals(30_000L, PreviewMapper.segments(timeline).single().sourceDurationMs)
    }
}
