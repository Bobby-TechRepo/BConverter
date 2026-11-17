package io.bsoft.bconverter.domain.font

import io.bsoft.bconverter.domain.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontLibraryTest {
    private val mem = mutableMapOf<String, String>()
    private val settings = object : SettingsStore {
        override fun getString(key: String) = mem[key]; override fun getBoolean(key: String, default: Boolean) = default
        override fun putString(key: String, value: String) { mem[key] = value }; override fun putBoolean(key: String, value: Boolean) {}
    }
    private fun f(fam: String, w: Int = 400, i: Boolean = false, s: FontSource = FontSource.SYSTEM) = FontFileRef(fam, FontVariant(w, i), "/$fam$w$i", s)
    private val lib = FontLibrary({ listOf(f("Roboto"), f("Roboto", 700), f("Roboto", 400, true), f("Noto Serif")) }, { listOf(f("Lobster", s = FontSource.DOWNLOADED)) }, RecentFonts(settings))

    @Test fun families_are_distinct_and_sorted() = assertEquals(listOf("Lobster", "Noto Serif", "Roboto"), lib.families())
    @Test fun variants_of_a_family_are_listed_upright_first() = assertEquals(listOf(FontVariant(400), FontVariant(700), FontVariant(400, true)), lib.variantsOf("roboto"))
    @Test fun resolving_uses_downloaded_and_system_fonts() { assertEquals("Lobster", lib.resolve("Lobster").usedFamily); assertTrue(lib.resolve("Roboto", 700).exact) }
    @Test fun an_uninstalled_family_resolves_to_the_fallback() { val r = lib.resolve("Zapfino"); assertEquals("Roboto", r.usedFamily); assertFalse(r.exact) }
    @Test fun recents_only_list_fonts_that_are_still_installed() { lib.markUsed("Lobster"); lib.markUsed("Gone Font"); lib.markUsed("Roboto"); assertEquals(listOf("Roboto", "Lobster"), lib.recentFamilies()) }
}
