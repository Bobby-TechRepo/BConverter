package io.bsoft.bconverter.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        /** Anything unknown (missing, damaged, from a newer version) means [SYSTEM]. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Wallpaper (dynamic) colours are opt-in: the brand palette is the default look. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Labs: preview the editor through Media3's composition player (step 20). Off by default. */
    val compositionPreview: Boolean = false,
)

object ThemePolicy {
    fun useDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColor(enabled: Boolean)
    fun setCompositionPreview(enabled: Boolean)
}

/** Seam over the platform's key/value storage. */
interface SettingsStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}

class PreferencesSettingsRepository(private val store: SettingsStore) : SettingsRepository {
    private val state = MutableStateFlow(
        AppSettings(ThemeMode.fromStored(store.getString(KEY_THEME)), store.getBoolean(KEY_DYNAMIC, false), store.getBoolean(KEY_COMPOSITION_PREVIEW, false)),
    )
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        store.putString(KEY_THEME, mode.name)
        state.value = state.value.copy(themeMode = mode)
    }

    override fun setDynamicColor(enabled: Boolean) {
        store.putBoolean(KEY_DYNAMIC, enabled)
        state.value = state.value.copy(dynamicColor = enabled)
    }

    override fun setCompositionPreview(enabled: Boolean) {
        store.putBoolean(KEY_COMPOSITION_PREVIEW, enabled)
        state.value = state.value.copy(compositionPreview = enabled)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_COMPOSITION_PREVIEW = "labs_composition_preview"
    }
}
