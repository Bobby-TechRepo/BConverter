package io.bsoft.bconverter.domain.font

import io.bsoft.bconverter.domain.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFontsTest {
    private class MemStore : SettingsStore {
        val m = mutableMapOf<String, Any>()
        override fun getString(key: String) = m[key] as String?
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun putString(key: String, value: String) { m[key] = value }
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
    }
    private val store = MemStore()

    @Test fun starts_empty() = assertTrue(RecentFonts(store).list().isEmpty())
    @Test fun newest_first_and_no_duplicates() {
        val r = RecentFonts(store); r.add("A"); r.add("B"); r.add("a")
        assertEquals(listOf("a", "B"), r.list())
    }
    @Test fun only_the_latest_few_are_kept() { val r = RecentFonts(store, max = 3); listOf("A", "B", "C", "D").forEach(r::add); assertEquals(listOf("D", "C", "B"), r.list()) }
    @Test fun survives_a_new_object() { RecentFonts(store).add("Lobster"); assertEquals(listOf("Lobster"), RecentFonts(store).list()) }
    @Test fun blank_names_are_ignored() { val r = RecentFonts(store); r.add("  "); assertTrue(r.list().isEmpty()) }
    @Test fun remove_works() { val r = RecentFonts(store); r.add("A"); r.add("B"); r.remove("a"); assertEquals(listOf("B"), r.list()) }
}
