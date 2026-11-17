package io.bsoft.bconverter.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.domain.settings.AppLanguage
import io.bsoft.bconverter.domain.settings.LanguageController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LanguageViewModel @Inject constructor(private val controller: LanguageController) : ViewModel() {
    private val _language = MutableStateFlow(controller.current())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun select(language: AppLanguage) {
        if (language == _language.value) return
        _language.value = language
        controller.set(language) // the screen is recreated in the new language right after this
    }
}
