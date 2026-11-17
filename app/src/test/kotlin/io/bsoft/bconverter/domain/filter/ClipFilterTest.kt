package io.bsoft.bconverter.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipFilterTest {
    @Test fun the_default_filter_is_the_identity() {
        assertTrue(ClipFilter.None.isIdentity)
        assertEquals(ClipFilter(), ClipFilter.None)
    }
    @Test fun changing_any_value_makes_it_not_the_identity() {
        assertFalse(ClipFilter(brightness = 0.1f).isIdentity)
        assertFalse(ClipFilter(tone = ToneMode.SEPIA).isIdentity)
        assertFalse(ClipFilter(invert = true).isIdentity)
    }
    @Test(expected = IllegalArgumentException::class) fun values_outside_the_range_are_rejected() { ClipFilter(contrast = 1.5f) }
    @Test(expected = IllegalArgumentException::class) fun a_negative_unsigned_value_is_rejected() { ClipFilter(blur = -0.1f) }
    @Test(expected = IllegalArgumentException::class) fun nan_is_rejected() { ClipFilter(hue = Float.NaN) }

    @Test fun every_param_reads_and_writes_its_own_field() {
        for (p in FilterParam.entries) {
            val v = p.max
            val f = p.set(ClipFilter.None, v)
            assertEquals("${p.name} read-back", v, p.get(f), 0f)
            // exactly this param changed
            for (other in FilterParam.entries) if (other != p) assertEquals("${other.name} untouched by ${p.name}", other.default, other.get(f), 0f)
        }
    }
    @Test fun setting_a_param_clamps_to_its_range() {
        assertEquals(1f, FilterParam.BRIGHTNESS.get(FilterParam.BRIGHTNESS.set(ClipFilter.None, 5f)), 0f)
        assertEquals(0f, FilterParam.BLUR.get(FilterParam.BLUR.set(ClipFilter.None, -3f)), 0f)
    }
    @Test fun every_param_defaults_to_the_identity_value() {
        for (p in FilterParam.entries) assertEquals(p.default, p.get(ClipFilter.None), 0f)
        assertTrue(FilterParam.entries.all { it.min <= it.default && it.default <= it.max })
    }
    @Test fun the_four_groups_are_all_populated() {
        for (g in FilterGroup.entries) assertTrue(g.name, FilterParam.entries.any { it.group == g })
    }
    @Test fun param_keys_are_unique() {
        assertEquals(FilterParam.entries.size, FilterParam.entries.map { it.key }.toSet().size)
    }
    @Test fun reset_returns_to_none_but_keeps_nothing() {
        assertEquals(ClipFilter.None, ClipFilter(brightness = 0.3f, tone = ToneMode.BLACK_WHITE, invert = true, presetId = "x").reset())
    }
    @Test fun editing_a_value_forgets_the_preset_name() {
        val f = FilterPresets.byId("vivid")!!.filter
        assertEquals("vivid", f.presetId)
        assertEquals(null, FilterParam.BRIGHTNESS.set(f, 0.2f).presetId)
    }
}
