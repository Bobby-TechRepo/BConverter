package io.bsoft.bconverter.feature.editor

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.domain.settings.SettingsRepository
import javax.inject.Inject

/** Which preview engine the editor uses (step 20); fixed for the editor's lifetime. */
@HiltViewModel
class PreviewEngineViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val compositionPreview: Boolean = settings.settings.value.compositionPreview
}
