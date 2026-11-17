package io.bsoft.bconverter.domain.font

import io.bsoft.bconverter.domain.settings.SettingsStore

/** The families used most recently, newest first, kept across launches. */
class RecentFonts(private val store: SettingsStore, private val max: Int = 8) {
    fun list(): List<String> = store.getString(KEY)?.split('\n')?.filter { it.isNotBlank() }?.take(max) ?: emptyList()

    fun add(family: String) {
        val name = family.trim().replace('\n', ' ')
        if (name.isEmpty()) return
        val updated = (listOf(name) + list().filterNot { FontNames.key(it) == FontNames.key(name) }).take(max)
        store.putString(KEY, updated.joinToString("\n"))
    }

    fun remove(family: String) = store.putString(KEY, list().filterNot { FontNames.key(it) == FontNames.key(family) }.joinToString("\n"))

    private companion object { const val KEY = "recent_fonts" }
}
