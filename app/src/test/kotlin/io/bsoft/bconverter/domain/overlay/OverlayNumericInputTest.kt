package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayNumericInputTest {
    @Test fun percent_accepts_plain_signed_and_comma_forms() {
        assertEquals(0.5f, OverlayNumericInput.percent("50")); assertEquals(0.5f, OverlayNumericInput.percent(" 50% ")); assertEquals(0.125f, OverlayNumericInput.percent("12,5")); assertEquals(0.125f, OverlayNumericInput.percent("12.5"))
    }
    @Test fun percent_respects_the_range() {
        assertNull(OverlayNumericInput.percent("101")); assertNull(OverlayNumericInput.percent("-1")); assertEquals(2f, OverlayNumericInput.percent("200", 10f, 2000f)); assertNull(OverlayNumericInput.percent("5", 10f, 2000f))
    }
    @Test fun nonsense_is_rejected() { listOf("", "abc", "1.2.3", "--5", "5e3", "NaN", "Infinity").forEach { assertNull(it, OverlayNumericInput.percent(it, -1000f, 1000f)) } }
    @Test fun degrees_allow_negatives_and_a_degree_sign() { assertEquals(-45.5f, OverlayNumericInput.degrees("-45.5°")); assertEquals(90f, OverlayNumericInput.degrees("90")); assertNull(OverlayNumericInput.degrees("x")); assertNull(OverlayNumericInput.degrees("1000000")) }
    @Test fun fractions_are_shown_without_trailing_zeros() { assertEquals("50", OverlayNumericInput.percentText(0.5f)); assertEquals("12.5", OverlayNumericInput.percentText(0.125f)); assertEquals("0", OverlayNumericInput.percentText(0f)); assertEquals("1.5", OverlayNumericInput.numberText(1.5f)) }
    @Test fun text_round_trips() { listOf(0f, 0.25f, 0.333f, 1f).forEach { f -> assertEquals(OverlayNumericInput.percentText(f), OverlayNumericInput.percentText(OverlayNumericInput.percent(OverlayNumericInput.percentText(f))!!)) } }
}
