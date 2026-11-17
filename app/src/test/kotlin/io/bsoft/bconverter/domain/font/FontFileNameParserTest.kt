package io.bsoft.bconverter.domain.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontFileNameParserTest {
    private fun p(n: String) = FontFileNameParser.parse(n)!!

    @Test fun plain_regular() { val r = p("Roboto-Regular.ttf"); assertEquals("Roboto", r.family); assertEquals(FontVariant(400), r.variant); assertFalse(r.isVariable) }
    @Test fun bold_italic() = assertEquals(FontVariant(700, true), p("Roboto-BoldItalic.ttf").variant)
    @Test fun weights_by_name() {
        assertEquals(100, p("X-Thin.ttf").variant.weight); assertEquals(200, p("X-ExtraLight.ttf").variant.weight); assertEquals(300, p("X-Light.ttf").variant.weight)
        assertEquals(500, p("X-Medium.ttf").variant.weight); assertEquals(600, p("X-SemiBold.ttf").variant.weight); assertEquals(800, p("X-ExtraBold.ttf").variant.weight); assertEquals(900, p("X-Black.ttf").variant.weight)
    }
    @Test fun camel_case_family_gets_spaces() { assertEquals("Noto Serif", p("NotoSerif-Regular.ttf").family); assertEquals("Roboto Condensed", p("RobotoCondensed-Bold.ttf").family) }
    @Test fun a_file_without_a_style_is_regular() { val r = p("DroidSans.ttf"); assertEquals("Droid Sans", r.family); assertEquals(FontVariant(400), r.variant) }
    @Test fun variable_fonts_are_recognised() { val r = p("Roboto[wdth,wght].ttf"); assertEquals("Roboto", r.family); assertTrue(r.isVariable) }
    @Test fun variable_italic_file() { val r = p("Roboto-Italic[wdth,wght].ttf"); assertEquals("Roboto", r.family); assertTrue(r.isVariable); assertTrue(r.variant.italic) }
    @Test fun otf_and_ttc_are_accepted() { assertEquals("Foo", p("Foo-Regular.otf").family); assertEquals("Noto Sans CJK", p("NotoSansCJK-Regular.ttc").family) }
    @Test fun other_files_and_emoji_are_ignored() {
        assertNull(FontFileNameParser.parse("fonts.xml")); assertNull(FontFileNameParser.parse("NotoColorEmoji.ttf")); assertNull(FontFileNameParser.parse("NotoSansSymbols-Regular.ttf")); assertNull(FontFileNameParser.parse("-Bold.ttf"))
    }
}
