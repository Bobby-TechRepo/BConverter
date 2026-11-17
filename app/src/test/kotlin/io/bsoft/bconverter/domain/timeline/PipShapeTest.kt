package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipShapeTest {
    private val withPip = ok(TimelineOperations.addPip(TL.timeline(TL.video("a", 10_000)), PipClip("p", TL.source("p"), 9_000, 0, 4_000, 2_000)))

    @Test fun a_clip_is_a_rectangle_until_told_otherwise() { assertEquals(PipShape.RECTANGLE, withPip.pipClips.single().shape) }
    @Test fun the_shape_can_be_set_and_the_rest_is_kept() {
        val c = ok(TimelineOperations.setPipShape(withPip, "p", PipShape.CIRCLE)).pipClips.single()
        assertEquals(PipShape.CIRCLE, c.shape); assertEquals(4_000L, c.durationMs); assertEquals(PipLayout(), c.layout)
    }
    @Test fun the_shape_of_an_unknown_clip_is_refused() { assertTrue(TimelineOperations.setPipShape(withPip, "zz", PipShape.ROUNDED) is EditResult.Failure) }
    @Test fun the_shape_survives_a_layout_change() {
        val round = ok(TimelineOperations.setPipShape(withPip, "p", PipShape.ROUNDED))
        assertEquals(PipShape.ROUNDED, ok(TimelineOperations.setPipLayout(round, "p", PipLayout(centerX = 0.4f))).pipClips.single().shape)
    }
}
