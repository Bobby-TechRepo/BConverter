package io.bsoft.bconverter.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test fun an_empty_or_missing_locale_list_means_follow_the_system() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("  "))
    }

    @Test fun a_tag_maps_to_its_language_whatever_the_region() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-GB"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTags("fr-CA"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTags("es_MX"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromTags("ja-JP"))
    }

    @Test fun every_chinese_variant_uses_the_simplified_chinese_translation() {
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTags("zh-CN"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTags("zh-Hans-CN"))
    }

    @Test fun only_the_first_locale_of_a_list_counts() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTags("fr-FR,en-US"))
    }

    @Test fun a_language_the_app_does_not_ship_falls_back_to_the_system_choice() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("de-DE"))
    }

    @Test fun a_choice_survives_a_round_trip_through_its_tag() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTags(it.tag)) }
    }

    @Test fun only_the_system_choice_has_no_native_name() {
        assertNull(AppLanguage.SYSTEM.nativeName)
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach { assertEquals(false, it.nativeName.isNullOrBlank()) }
    }
}
