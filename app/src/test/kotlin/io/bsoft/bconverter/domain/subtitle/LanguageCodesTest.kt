package io.bsoft.bconverter.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageCodesTest {
    @Test fun two_letter_codes_become_three_letter() { assertEquals("eng", LanguageCodes.iso639_2("en")); assertEquals("fra", LanguageCodes.iso639_2("FR")); assertEquals("zho", LanguageCodes.iso639_2("zh")); assertEquals("jpn", LanguageCodes.iso639_2("ja")) }
    @Test fun three_letter_codes_pass_through_lowercased() = assertEquals("spa", LanguageCodes.iso639_2("SPA"))
    @Test fun regions_are_dropped() = assertEquals("eng", LanguageCodes.iso639_2("en-US"))
    @Test fun unknown_or_blank_codes_are_null() { assertNull(LanguageCodes.iso639_2("")); assertNull(LanguageCodes.iso639_2(null)); assertNull(LanguageCodes.iso639_2("xx")); assertNull(LanguageCodes.iso639_2("klingon")) }
}
