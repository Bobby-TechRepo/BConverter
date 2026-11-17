package io.bsoft.bconverter.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.domain.about.AppInfo
import io.bsoft.bconverter.domain.about.AppInfoProvider
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val provider: AppInfoProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _info = MutableStateFlow<AppInfo?>(null)
    /** Null until loaded (reading the engine version loads the native library). */
    val info: StateFlow<AppInfo?> = _info.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _info.value = withContext(io) { provider.load() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _info.value = AppInfo("unknown", 0, null)
            }
        }
    }
}
