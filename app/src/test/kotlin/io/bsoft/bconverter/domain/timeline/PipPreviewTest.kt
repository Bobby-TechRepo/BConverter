package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipPreviewTest {
    private fun pip(id: String, start: Long, from: Long = 1_000, to: Long = 5_000) = PipClip(id, TL.source(id), 9_000, from, to, start)

    @Test fun a_clip_shows_from_its_start_up_to_but_not_including_its_end() {
        val p = pip("a", 2_000)
        assertTrue(PipPreview.activeAt(listOf(p), 1_999).isEmpty())
        assertEquals(1, PipPreview.activeAt(listOf(p), 2_000).size)
        assertEquals(1, PipPreview.activeAt(listOf(p), 5_999).size)
        assertTrue(PipPreview.activeAt(listOf(p), 6_000).isEmpty())
    }
    @Test fun the_source_position_follows_the_trim_and_the_start() {
        assertEquals(1_000L, PipPreview.activeAt(listOf(pip("a", 2_000)), 2_000).single().sourcePositionMs)
        assertEquals(2_500L, PipPreview.activeAt(listOf(pip("a", 2_000)), 3_500).single().sourcePositionMs)
    }
    @Test fun clips_showing_together_keep_their_back_to_front_order() {
        val list = listOf(pip("back", 0), pip("front", 1_000))
        assertEquals(listOf("back", "front"), PipPreview.activeAt(list, 2_000).map { it.clip.id })
    }
    @Test fun only_the_clips_showing_are_returned() {
        assertEquals(listOf("b"), PipPreview.activeAt(listOf(pip("a", 0), pip("b", 10_000)), 11_000).map { it.clip.id })
    }

    private val base = PipLayout(0.5f, 0.5f, 0.4f, 1f)
    @Test fun dragging_moves_the_centre_by_the_finger_movement() {
        val l = PipPreview.transformed(base, 0.1f, -0.2f, 1f)
        assertEquals(0.6f, l.centerX, 0.0001f); assertEquals(0.3f, l.centerY, 0.0001f); assertEquals(0.4f, l.width, 0.0001f)
    }
    @Test fun the_centre_cannot_leave_the_frame() {
        val l = PipPreview.transformed(base, 3f, -3f, 1f)
        assertEquals(1f, l.centerX, 0f); assertEquals(0f, l.centerY, 0f)
    }
    @Test fun pinching_scales_the_width_within_its_limits() {
        assertEquals(0.8f, PipPreview.transformed(base, 0f, 0f, 2f).width, 0.0001f)
        assertEquals(PipLayout.MAX_WIDTH, PipPreview.transformed(base, 0f, 0f, 10f).width, 0f)
        assertEquals(PipLayout.MIN_WIDTH, PipPreview.transformed(base, 0f, 0f, 0.01f).width, 0f)
    }
    @Test fun opacity_is_left_alone() { assertEquals(0.5f, PipPreview.transformed(base.copy(opacity = 0.5f), 0.1f, 0.1f, 1.2f).opacity, 0f) }
    @Test fun nonsense_input_changes_nothing() {
        assertEquals(base, PipPreview.transformed(base, Float.NaN, 0f, 1f))
        assertEquals(base, PipPreview.transformed(base, 0f, 0f, 0f))
        assertEquals(base, PipPreview.transformed(base, 0f, 0f, -1f))
    }
    @Test fun a_centre_near_the_middle_snaps_to_it() {
        assertEquals(0.5f, PipPreview.snapped(0.515f), 0f)
        assertEquals(0.53f, PipPreview.snapped(0.53f), 0f)
    }
}
