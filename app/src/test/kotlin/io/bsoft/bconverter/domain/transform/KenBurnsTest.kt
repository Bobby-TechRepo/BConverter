package io.bsoft.bconverter.domain.transform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KenBurnsTest {
    @Test fun none_is_the_default_and_has_no_motion() {
        assertTrue(KenBurns.None.isNone); assertEquals(KenBurns.None.start(), KenBurns.None.end())
    }
    @Test fun zoom_in_goes_from_the_whole_picture_to_the_amount() {
        val k = KenBurns(KenBurnsMode.ZOOM_IN, 1.5f)
        assertEquals(KenBurnsWindow(1f, 0.5f, 0.5f), k.start()); assertEquals(KenBurnsWindow(1.5f, 0.5f, 0.5f), k.end())
    }
    @Test fun zoom_out_is_zoom_in_backwards() {
        val a = KenBurns(KenBurnsMode.ZOOM_IN, 1.8f); val b = KenBurns(KenBurnsMode.ZOOM_OUT, 1.8f)
        assertEquals(a.start(), b.end()); assertEquals(a.end(), b.start())
    }
    @Test fun a_pan_keeps_its_zoom_and_slides_edge_to_edge() {
        val k = KenBurns(KenBurnsMode.PAN_LEFT, 2f)
        assertEquals(KenBurnsWindow(2f, 0.75f, 0.5f), k.start()); assertEquals(KenBurnsWindow(2f, 0.25f, 0.5f), k.end())
        val up = KenBurns(KenBurnsMode.PAN_UP, 2f)
        assertEquals(KenBurnsWindow(2f, 0.5f, 0.75f), up.start()); assertEquals(KenBurnsWindow(2f, 0.5f, 0.25f), up.end())
    }
    @Test fun every_window_stays_inside_the_picture() {
        for (mode in KenBurnsMode.entries) for (a in listOf(1.1f, 1.3f, 2f)) {
            val k = KenBurns(mode, a)
            for (w in listOf(k.start(), k.end())) {
                assertTrue("$mode $a", w.cx - 0.5f / w.zoom >= -1e-5f && w.cx + 0.5f / w.zoom <= 1f + 1e-5f)
                assertTrue("$mode $a", w.cy - 0.5f / w.zoom >= -1e-5f && w.cy + 0.5f / w.zoom <= 1f + 1e-5f)
            }
        }
    }
    @Test fun the_amount_must_be_in_range() {
        for (bad in listOf(1.0f, 2.5f, Float.NaN)) try { KenBurns(KenBurnsMode.ZOOM_IN, bad); fail("accepted $bad") } catch (_: IllegalArgumentException) {}
    }
}
