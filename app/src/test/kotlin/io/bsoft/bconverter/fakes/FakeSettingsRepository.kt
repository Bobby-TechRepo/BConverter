package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.settings.AppSettings
import io.bsoft.bconverter.domain.settings.SettingsRepository
import io.bsoft.bconverter.domain.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state
    override fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override fun setDynamicColor(enabled: Boolean) { state.value = state.value.copy(dynamicColor = enabled) }
    override fun setCompositionPreview(enabled: Boolean) { state.value = state.value.copy(compositionPreview = enabled) }
}
