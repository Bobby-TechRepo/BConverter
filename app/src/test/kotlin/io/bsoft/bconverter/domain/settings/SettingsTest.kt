package io.bsoft.bconverter.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private class MemoryStore(val values: MutableMap<String, Any> = mutableMapOf()) : SettingsStore {
        override fun getString(key: String): String? = values[key] as? String
        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
        override fun putString(key: String, value: String) { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    }

    // ---- theme policy ----
    @Test fun system_follows_the_device() {
        assertTrue(ThemePolicy.useDark(ThemeMode.SYSTEM, systemDark = true)); assertFalse(ThemePolicy.useDark(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test fun light_and_dark_ignore_the_device() {
        assertFalse(ThemePolicy.useDark(ThemeMode.LIGHT, systemDark = true)); assertTrue(ThemePolicy.useDark(ThemeMode.DARK, systemDark = false))
    }

    @Test fun stored_theme_names_round_trip_and_anything_else_means_system() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null)); assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("PURPLE"))
    }

    // ---- repository ----
    @Test fun a_fresh_install_follows_the_system_with_the_brand_colours() {
        assertEquals(AppSettings(ThemeMode.SYSTEM, dynamicColor = false), PreferencesSettingsRepository(MemoryStore()).settings.value)
    }

    @Test fun changes_are_published_and_persisted() {
        val store = MemoryStore(); val repo = PreferencesSettingsRepository(store)
        repo.setThemeMode(ThemeMode.DARK); repo.setDynamicColor(false)
        assertEquals(AppSettings(ThemeMode.DARK, false), repo.settings.value)
        assertEquals(AppSettings(ThemeMode.DARK, false), PreferencesSettingsRepository(store).settings.value)
    }

    @Test fun the_labs_preview_engine_is_off_until_switched_on_and_is_remembered() {
        val store = MemoryStore(); val repo = PreferencesSettingsRepository(store)
        assertFalse(repo.settings.value.compositionPreview)
        repo.setCompositionPreview(true)
        assertTrue(repo.settings.value.compositionPreview)
        assertTrue(PreferencesSettingsRepository(store).settings.value.compositionPreview)
    }

    @Test fun a_damaged_stored_value_falls_back_to_the_default() {
        val repo = PreferencesSettingsRepository(MemoryStore(mutableMapOf("theme_mode" to "???")))
        assertEquals(ThemeMode.SYSTEM, repo.settings.value.themeMode)
    }

    @Test fun setting_the_same_value_again_changes_nothing() {
        val repo = PreferencesSettingsRepository(MemoryStore()); repo.setThemeMode(ThemeMode.LIGHT)
        val before = repo.settings.value; repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(before, repo.settings.value)
    }
}
