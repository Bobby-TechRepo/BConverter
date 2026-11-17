package io.bsoft.bconverter.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPresetsTest {
    @Test fun there_is_a_decent_number_of_presets() { assertTrue(FilterPresets.all.size >= 12) }
    @Test fun ids_are_unique() { assertEquals(FilterPresets.all.size, FilterPresets.all.map { it.id }.toSet().size) }
    @Test fun the_first_preset_is_the_original() {
        assertEquals("original", FilterPresets.all.first().id)
        assertTrue(FilterPresets.all.first().filter.isIdentity)
    }
    @Test fun every_other_preset_changes_the_picture() {
        for (p in FilterPresets.all.drop(1)) assertFalse(p.id, p.filter.isIdentity)
    }
    @Test fun a_preset_carries_its_own_id() {
        for (p in FilterPresets.all.drop(1)) assertEquals(p.id, p.filter.presetId)
    }
    @Test fun byId_finds_and_misses() {
        assertNotNull(FilterPresets.byId("noir")); assertEquals(null, FilterPresets.byId("nope"))
    }
    @Test fun noir_is_black_and_white_and_sepia_is_sepia() {
        assertEquals(ToneMode.BLACK_WHITE, FilterPresets.byId("noir")!!.filter.tone)
        assertEquals(ToneMode.SEPIA, FilterPresets.byId("sepia")!!.filter.tone)
    }
    @Test fun every_preset_has_a_name_resource_key() {
        for (p in FilterPresets.all) assertEquals("filter_preset_${p.id}", p.nameKey)
    }
}
