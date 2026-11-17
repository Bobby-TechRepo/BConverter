package io.bsoft.bconverter.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.domain.settings.AppSettings
import io.bsoft.bconverter.domain.settings.CacheStorage
import io.bsoft.bconverter.domain.settings.SettingsRepository
import io.bsoft.bconverter.domain.settings.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val cacheBytes: Long = 0,
    val clearing: Boolean = false,
    /** Bytes freed by the last clear; null until one has finished. */
    val freedBytes: Long? = null,
    val clearFailed: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val cache: CacheStorage,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(settings = repository.settings.value))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.settings.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch { measure() }
    }

    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = repository.setDynamicColor(enabled)
    fun setCompositionPreview(enabled: Boolean) = repository.setCompositionPreview(enabled)

    fun clearCache() {
        if (_state.value.clearing) return
        _state.update { it.copy(clearing = true, freedBytes = null, clearFailed = false) }
        viewModelScope.launch {
            try {
                val freed = withContext(io) { cache.clear() }
                val left = withContext(io) { cache.usageBytes() }
                _state.update { it.copy(clearing = false, freedBytes = freed, cacheBytes = left) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(clearing = false, clearFailed = true) }
            }
        }
    }

    private suspend fun measure() {
        try {
            val bytes = withContext(io) { cache.usageBytes() }
            _state.update { it.copy(cacheBytes = bytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { /* an unknown size is shown as 0; clearing still works */ }
    }
}
