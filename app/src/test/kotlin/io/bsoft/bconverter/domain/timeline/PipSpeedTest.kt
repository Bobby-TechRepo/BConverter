package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipSpeedTest {
    private fun clip(speed: Float = 1f) = PipClip("p", TL.source("p"), 9_000, 1_000, 5_000, 2_000, speed = speed)
    private val withPip = ok(TimelineOperations.addPip(TL.timeline(TL.video("a", 10_000)), clip()))

    @Test fun at_normal_speed_the_clip_lasts_its_source_span() { assertEquals(4_000L, clip().durationMs); assertEquals(6_000L, clip().endTimeMs) }
    @Test fun a_faster_clip_is_shorter_on_the_timeline() { assertEquals(2_000L, clip(2f).durationMs); assertEquals(1_000L, clip(4f).durationMs) }
    @Test fun a_slower_clip_is_longer_on_the_timeline() { assertEquals(8_000L, clip(0.5f).durationMs); assertEquals(16_000L, clip(0.25f).durationMs) }
    @Test fun a_speed_outside_the_range_is_not_a_clip() {
        assertTrue(runCatching { clip(0.1f) }.isFailure); assertTrue(runCatching { clip(5f) }.isFailure); assertTrue(runCatching { clip(Float.NaN) }.isFailure)
    }
    @Test fun the_speed_can_be_set_and_the_clip_keeps_its_source_range() {
        val c = ok(TimelineOperations.setPipSpeed(withPip, "p", 2f)).pipClips.single()
        assertEquals(2f, c.speed); assertEquals(1_000L, c.sourceStartMs); assertEquals(5_000L, c.sourceEndMs); assertEquals(2_000L, c.durationMs)
    }
    @Test fun a_bad_speed_or_unknown_clip_is_refused() {
        assertTrue(TimelineOperations.setPipSpeed(withPip, "p", 9f) is EditResult.Failure)
        assertTrue(TimelineOperations.setPipSpeed(withPip, "zz", 2f) is EditResult.Failure)
    }
    @Test fun the_source_position_follows_the_speed() {
        val c = clip(2f)
        assertEquals(1_000L, PipPreview.activeAt(listOf(c), 2_000).single().sourcePositionMs)
        assertEquals(2_000L, PipPreview.activeAt(listOf(c), 2_500).single().sourcePositionMs)
        assertEquals(4_000L, PipPreview.activeAt(listOf(c), 3_500).single().sourcePositionMs)
    }
    @Test fun a_faster_clip_stops_showing_when_it_ends_on_the_timeline() {
        assertEquals(0, PipPreview.activeAt(listOf(clip(2f)), 4_000).size); assertEquals(1, PipPreview.activeAt(listOf(clip(2f)), 3_999).size)
    }
    @Test fun the_speed_is_kept_by_layout_changes_and_fades() {
        val fast = ok(TimelineOperations.setPipSpeed(withPip, "p", 2f))
        assertEquals(2f, ok(TimelineOperations.setPipLayout(fast, "p", PipLayout(centerX = 0.3f))).pipClips.single().speed)
        assertEquals(2f, ok(TimelineOperations.setPipFades(fast, "p", 100, 100)).pipClips.single().speed)
    }
}
