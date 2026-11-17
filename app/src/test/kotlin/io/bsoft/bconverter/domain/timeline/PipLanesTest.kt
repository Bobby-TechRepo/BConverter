package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipLanesTest {
    private fun pip(id: String, start: Long, span: Long) = PipClip(id, TL.source(id), span + 1_000, 0, span, start)

    @Test fun no_clips_need_no_rows() { assertTrue(PipLanes.assign(emptyList()).isEmpty()) }
    @Test fun a_single_clip_is_in_the_first_row() { assertEquals(mapOf("a" to 0), PipLanes.assign(listOf(pip("a", 0, 2_000)))) }
    @Test fun clips_apart_in_time_share_a_row() { assertEquals(0, PipLanes.assign(listOf(pip("a", 0, 1_000), pip("b", 5_000, 1_000)))["b"]) }
    @Test fun clips_that_touch_share_a_row() { assertEquals(0, PipLanes.assign(listOf(pip("a", 0, 1_000), pip("b", 1_000, 1_000)))["b"]) }
    @Test fun overlapping_clips_get_their_own_rows() {
        val rows = PipLanes.assign(listOf(pip("a", 0, 4_000), pip("b", 1_000, 4_000), pip("c", 2_000, 1_000)))
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2), rows)
    }
    @Test fun a_freed_row_is_reused() {
        val rows = PipLanes.assign(listOf(pip("a", 0, 2_000), pip("b", 1_000, 5_000), pip("c", 3_000, 1_000)))
        assertEquals(0, rows["c"])
    }
    @Test fun the_input_order_does_not_matter() {
        val a = pip("a", 0, 4_000); val b = pip("b", 1_000, 4_000)
        assertEquals(PipLanes.assign(listOf(a, b)), PipLanes.assign(listOf(b, a)))
    }
}
