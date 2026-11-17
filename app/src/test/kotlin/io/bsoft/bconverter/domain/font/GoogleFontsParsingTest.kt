package io.bsoft.bconverter.domain.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleFontsParsingTest {
    private val meta = ")]}'\n" + """{"familyMetadataList":[
      {"family":"Open Sans","category":"Sans Serif","popularity":2,"license":"ofl","fonts":{"400":{},"700":{},"400i":{},"300":{}}},
      {"family":"Lobster","category":"Display","popularity":50,"license":"ufl","fonts":{"400":{}}},
      {"family":"Broken","category":"x","fonts":{}},
      {"family":"Roboto","category":"Sans Serif","fonts":{"100":{},"900":{},"weird":{}}}
    ]}"""

    @Test fun the_catalog_parses_with_its_safety_prefix() {
        val c = GoogleFonts.parseCatalog(meta)
        assertEquals(listOf("Open Sans", "Lobster", "Roboto"), c.map { it.family })
        val o = c.first()
        assertEquals("sans serif", o.category); assertEquals(2, o.popularity); assertEquals(FontLicense.OFL, o.license)
        assertEquals(listOf(FontVariant(300), FontVariant(400), FontVariant(700), FontVariant(400, true)), o.variants)
    }
    @Test fun licences_map_and_unknown_is_generic_open_source() {
        val c = GoogleFonts.parseCatalog(meta)
        assertEquals(FontLicense.UBUNTU, c[1].license); assertEquals(FontLicense.OPEN_SOURCE, c[2].license)
    }
    @Test fun odd_variant_keys_are_skipped() = assertEquals(listOf(FontVariant(100), FontVariant(900)), GoogleFonts.parseCatalog(meta).last().variants)
    @Test fun garbage_gives_an_empty_catalog() { assertTrue(GoogleFonts.parseCatalog("<html>").isEmpty()); assertTrue(GoogleFonts.parseCatalog("").isEmpty()); assertTrue(GoogleFonts.parseCatalog("{}").isEmpty()) }

    @Test fun the_css_url_lists_variants_in_order() =
        assertEquals("https://fonts.googleapis.com/css2?family=Open+Sans:ital,wght@0,400;0,700;1,400", GoogleFonts.cssUrl("Open Sans", listOf(FontVariant(700), FontVariant(400, true), FontVariant(400), FontVariant(400))))
    @Test(expected = IllegalArgumentException::class) fun the_css_url_needs_a_variant() { GoogleFonts.cssUrl("X", emptyList()) }

    private val css = """
        /* latin */
        @font-face { font-family: 'Open Sans'; font-style: normal; font-weight: 400; src: url(https://fonts.gstatic.com/s/opensans/a.ttf) format('truetype'); }
        @font-face { font-family: 'Open Sans'; font-style: italic; font-weight: 700;
          src: url("https://fonts.gstatic.com/s/opensans/b.ttf") format('truetype'); }
        @font-face { font-family: 'Open Sans'; font-style: normal; font-weight: 400; src: url(https://fonts.gstatic.com/s/opensans/dup.ttf); }
        @font-face { font-family: 'Open Sans'; font-weight: 999; src: url(https://fonts.gstatic.com/x.ttf); }
    """
    @Test fun the_css_gives_one_link_per_variant() {
        val l = GoogleFonts.parseCss(css)
        assertEquals(listOf(FontVariant(400), FontVariant(700, true)), l.map { it.variant })
        assertEquals("https://fonts.gstatic.com/s/opensans/a.ttf", l[0].url); assertEquals("https://fonts.gstatic.com/s/opensans/b.ttf", l[1].url)
    }
    @Test fun css_without_font_faces_is_empty() = assertTrue(GoogleFonts.parseCss("body{}").isEmpty())

    @Test fun only_googles_https_hosts_are_allowed() {
        assertTrue(GoogleFonts.isAllowedUrl("https://fonts.gstatic.com/s/a.ttf")); assertTrue(GoogleFonts.isAllowedUrl("https://fonts.googleapis.com/css2?family=A"))
        assertFalse(GoogleFonts.isAllowedUrl("http://fonts.gstatic.com/a.ttf")); assertFalse(GoogleFonts.isAllowedUrl("https://evil.com/fonts.gstatic.com/a.ttf"))
        assertFalse(GoogleFonts.isAllowedUrl("https://fonts.gstatic.com.evil.com/a.ttf")); assertFalse(GoogleFonts.isAllowedUrl("https://user@evil.com/a.ttf")); assertFalse(GoogleFonts.isAllowedUrl("file:///system/fonts/a.ttf"))
    }
}
