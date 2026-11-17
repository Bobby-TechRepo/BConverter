package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TextOverlayDefaultsTest {
    @Test fun a_new_overlay_starts_at_the_playhead_and_lasts_three_seconds() { val t = TextOverlayDefaults.newAt("n", 4000); assertEquals(4000L, t.startMs); assertEquals(7000L, t.endMs); assertEquals("Text", t.text) }
    @Test fun a_negative_playhead_is_treated_as_zero() = assertEquals(0L, TextOverlayDefaults.newAt("n", -5).startMs)
    @Test fun it_is_centred_and_readable() { val t = TextOverlayDefaults.newAt("n", 0); assertEquals(0.5f, t.centerX); assertEquals(0.5f, t.centerY); assertEquals(700, t.style.fontWeight); assertNotNull(t.style.outline) }
}
