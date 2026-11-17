package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generous time limits that only fail if something becomes accidentally quadratic or worse. */
class OverlayPerformanceTest {
    private val many = (1..1000).map { TextOverlay("t$it", "Line $it", it * 50L, it * 50L + 4000, centerX = (it % 10) / 10f + 0.05f) }
    private fun timeMs(block: () -> Unit): Long { val s = System.nanoTime(); block(); return (System.nanoTime() - s) / 1_000_000 }

    @Test fun lane_assignment_for_a_thousand_overlays_is_fast() {
        var rows = emptyMap<String, Int>()
        val ms = timeMs { rows = OverlayLanes.assign(many) }
        assertEquals(1000, rows.size); assertTrue("took $ms ms", ms < 2_000)
    }
    @Test fun hit_testing_a_thousand_overlays_is_fast() {
        val ms = timeMs { repeat(200) { OverlayHitTest.topmostAt(many, 20_000, 640f, 360f, 1280, 720) } }
        assertTrue("took $ms ms", ms < 2_000)
    }
    @Test fun layer_keys_for_a_thousand_overlays_are_fast_and_distinct_per_text() {
        var keys = emptySet<String>()
        val ms = timeMs { keys = many.map { OverlayLayerKey.of(OverlayRasterRequest(it, 1920, 1080, null, 0)) }.toSet() }
        assertEquals(1000, keys.size); assertTrue("took $ms ms", ms < 3_000)
    }
    @Test fun overlays_that_only_differ_in_time_and_position_share_one_layer_key() {
        val same = (1..200).map { TextOverlay("t$it", "Same", it * 10L, it * 10L + 1000, centerX = it / 300f, centerY = 0.5f) }
        assertEquals(1, same.map { OverlayLayerKey.of(OverlayRasterRequest(it, 1280, 720, null, 0)) }.toSet().size)
    }
    @Test fun animation_frames_for_many_overlays_are_cheap() {
        val animated = many.map { it.copy(animIn = OverlayAnimation(AnimationKind.SCALE), animOut = OverlayAnimation(AnimationKind.FADE)) }
        val ms = timeMs { for (t in 0L until 60_000L step 33L) animated.forEach { OverlayAnimator.frameAt(it, t) } }
        assertTrue("took $ms ms", ms < 5_000)
    }
}
