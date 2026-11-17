package io.bsoft.bconverter.domain.font

import io.bsoft.bconverter.domain.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledFamiliesTest {
    private val settings = object : SettingsStore {
        override fun getString(key: String): String? = null; override fun getBoolean(key: String, default: Boolean) = default
        override fun putString(key: String, value: String) {}; override fun putBoolean(key: String, value: Boolean) {}
    }
    private fun f(fam: String, w: Int = 400, s: FontSource = FontSource.SYSTEM, lic: FontLicense? = null) =
        if (lic == null) FontFileRef(fam, FontVariant(w), "/$fam$w", s) else FontFileRef(fam, FontVariant(w), "/$fam$w", s, license = lic)
    private val lib = FontLibrary(
        { listOf(f("Roboto"), f("Roboto", 700), f("Lobster")) },
        { listOf(f("Lobster", 400, FontSource.DOWNLOADED, FontLicense.UBUNTU), f("Open Sans", 300, FontSource.DOWNLOADED, FontLicense.OFL)) },
        RecentFonts(settings),
    )

    @Test fun one_row_per_family_sorted_by_name() = assertEquals(listOf("Lobster", "Open Sans", "Roboto"), lib.installedFamilies().map { it.name })
    @Test fun a_family_with_a_downloaded_file_is_marked_downloaded_with_that_licence() {
        val l = lib.installedFamilies().first { it.name == "Lobster" }
        assertTrue(l.downloaded); assertEquals(FontLicense.UBUNTU, l.license); assertEquals(FontSource.DOWNLOADED, l.sample!!.source)
    }
    @Test fun system_families_carry_the_device_licence_and_all_their_styles() {
        val r = lib.installedFamilies().first { it.name == "Roboto" }
        assertFalse(r.downloaded); assertEquals(FontLicense.SYSTEM, r.license); assertEquals(listOf(FontVariant(400), FontVariant(700)), r.variants)
    }
    @Test fun a_family_without_a_regular_style_still_gets_a_sample() = assertEquals(300, lib.installedFamilies().first { it.name == "Open Sans" }.sample!!.variant.weight)
}
