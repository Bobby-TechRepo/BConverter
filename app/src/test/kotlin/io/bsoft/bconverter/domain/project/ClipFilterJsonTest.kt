package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.FilterPresets
import io.bsoft.bconverter.domain.filter.ToneMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipFilterJsonTest {
    @Test fun a_default_filter_writes_nothing() {
        assertEquals(0, ClipFilterJson.encode(ClipFilter.None).length())
    }
    @Test fun every_preset_round_trips() {
        for (p in FilterPresets.all) assertEquals(p.id, p.filter, ClipFilterJson.decode(ClipFilterJson.encode(p.filter)))
    }
    @Test fun a_custom_filter_round_trips() {
        val f = ClipFilter(brightness = -0.3f, hue = 0.25f, fade = 0.6f, tone = ToneMode.SEPIA, invert = true)
        assertEquals(f, ClipFilterJson.decode(ClipFilterJson.encode(f)))
    }
    @Test fun only_changed_values_are_written() {
        val o = ClipFilterJson.encode(ClipFilter(contrast = 0.2f))
        assertEquals(setOf("contrast"), o.keys().asSequence().toSet())
        assertFalse(o.has("tone"))
    }
    @Test fun an_empty_object_is_the_identity() {
        assertEquals(ClipFilter.None, ClipFilterJson.decode(org.json.JSONObject()))
    }
    @Test fun unknown_keys_and_out_of_range_values_are_survived() {
        val o = org.json.JSONObject().put("contrast", 9.0).put("future", 1).put("tone", "NOPE")
        val f = ClipFilterJson.decode(o)
        assertEquals(1f, f.contrast, 0f); assertEquals(ToneMode.NONE, f.tone)
    }
}
