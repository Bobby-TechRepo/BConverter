package io.bsoft.bconverter.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.bsoft.bconverter.domain.settings.AppLanguage
import io.bsoft.bconverter.domain.settings.LanguageController

/** Per-app language through AppCompat: the platform setting on Android 13+, a stored preference applied by AppCompat before that. */
class AppCompatLanguageController : LanguageController {
    override fun current(): AppLanguage = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    override fun set(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag),
        )
    }
}
