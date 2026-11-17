package io.bsoft.bconverter.data.settings

import android.content.SharedPreferences
import io.bsoft.bconverter.domain.settings.SettingsStore

class SharedPreferencesSettingsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
}
