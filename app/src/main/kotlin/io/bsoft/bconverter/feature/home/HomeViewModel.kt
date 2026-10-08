package io.bsoft.bconverter.feature.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeDestination(val route: String, @StringRes val titleRes: Int) {
    CONVERT_VIDEO("convert", R.string.home_convert_video),
    EXTRACT_AUDIO("extract_audio", R.string.home_extract_audio),
    VIDEO_EDITOR("editor", R.string.home_video_editor),
    RECENT_PROJECTS("projects", R.string.home_recent_projects),
    SETTINGS("settings", R.string.home_settings),
    ABOUT("about", R.string.home_about),
}

data class HomeUiState(val destinations: List<HomeDestination>)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(HomeDestination.entries))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
