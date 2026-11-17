package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayLanesTest {
    private fun o(id: String, s: Long, e: Long) = TextOverlay(id, id, s, e)

    @Test fun overlays_that_do_not_overlap_share_a_row() = assertEquals(mapOf("a" to 0, "b" to 0), OverlayLanes.assign(listOf(o("a", 0, 100), o("b", 100, 200))))
    @Test fun overlapping_overlays_get_separate_rows() = assertEquals(mapOf("a" to 0, "b" to 1), OverlayLanes.assign(listOf(o("a", 0, 100), o("b", 50, 150))))
    @Test fun a_later_overlay_reuses_the_first_free_row() {
        val r = OverlayLanes.assign(listOf(o("a", 0, 100), o("b", 10, 300), o("c", 120, 200)))
        assertEquals(0, r["a"]); assertEquals(1, r["b"]); assertEquals(0, r["c"])
    }
    @Test fun row_count() { assertEquals(0, OverlayLanes.rowCount(emptyList())); assertEquals(2, OverlayLanes.rowCount(listOf(o("a", 0, 100), o("b", 50, 150)))) }
    @Test fun the_order_of_the_input_does_not_matter() = assertEquals(OverlayLanes.assign(listOf(o("a", 0, 100), o("b", 50, 150))), OverlayLanes.assign(listOf(o("b", 50, 150), o("a", 0, 100))))
}
