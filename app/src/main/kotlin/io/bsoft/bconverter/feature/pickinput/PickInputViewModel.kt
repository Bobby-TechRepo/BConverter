package io.bsoft.bconverter.feature.pickinput

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.repository.InputResolver
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PickKind(val mimeTypes: List<String>, @StringRes val selectLabelRes: Int) {
    VIDEO(listOf("video/*"), R.string.select_video),
    AUDIO(listOf("audio/*"), R.string.select_audio),
}

sealed interface PickInputState {
    data object Idle : PickInputState
    data object Resolving : PickInputState
    data class Ready(val input: ResolvedInput) : PickInputState
    data class Error(val error: InputError) : PickInputState
}

@HiltViewModel
class PickInputViewModel @Inject constructor(
    private val resolver: InputResolver,
) : ViewModel() {
    private val _state = MutableStateFlow<PickInputState>(PickInputState.Idle)
    val state: StateFlow<PickInputState> = _state.asStateFlow()
    private var job: Job? = null

    /** [uri] is null when the user dismissed the picker. */
    fun onPicked(uri: Uri?) {
        if (uri == null) return
        job?.cancel()
        releaseCurrent()
        _state.value = PickInputState.Resolving
        job = viewModelScope.launch {
            _state.value = when (val result = resolver.resolve(MediaSource.UriSource(uri))) {
                is ResolveResult.Success -> PickInputState.Ready(result.input)
                is ResolveResult.Failure -> PickInputState.Error(result.error)
            }
        }
    }

    fun reset() {
        job?.cancel()
        releaseCurrent()
        _state.value = PickInputState.Idle
    }

    private fun releaseCurrent() {
        (_state.value as? PickInputState.Ready)?.let { resolver.release(it.input) }
    }
}
