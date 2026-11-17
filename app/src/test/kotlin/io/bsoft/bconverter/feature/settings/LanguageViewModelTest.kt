package io.bsoft.bconverter.feature.settings

import io.bsoft.bconverter.domain.settings.AppLanguage
import io.bsoft.bconverter.domain.settings.LanguageController
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageViewModelTest {
    private class FakeController(var language: AppLanguage = AppLanguage.SYSTEM) : LanguageController {
        val calls = mutableListOf<AppLanguage>()
        override fun current() = language
        override fun set(language: AppLanguage) { this.language = language; calls += language }
    }

    @Test fun starts_with_the_current_language() {
        assertEquals(AppLanguage.FRENCH, LanguageViewModel(FakeController(AppLanguage.FRENCH)).language.value)
    }

    @Test fun selecting_a_language_applies_it_and_updates_the_state() {
        val controller = FakeController()
        val vm = LanguageViewModel(controller)
        vm.select(AppLanguage.JAPANESE)
        assertEquals(AppLanguage.JAPANESE, vm.language.value)
        assertEquals(listOf(AppLanguage.JAPANESE), controller.calls)
    }

    @Test fun selecting_the_language_already_in_use_does_nothing() {
        val controller = FakeController(AppLanguage.SPANISH)
        LanguageViewModel(controller).select(AppLanguage.SPANISH)
        assertEquals(emptyList<AppLanguage>(), controller.calls)
    }

    @Test fun going_back_to_the_system_language_is_applied() {
        val controller = FakeController(AppLanguage.ENGLISH)
        LanguageViewModel(controller).select(AppLanguage.SYSTEM)
        assertEquals(listOf(AppLanguage.SYSTEM), controller.calls)
    }
}
