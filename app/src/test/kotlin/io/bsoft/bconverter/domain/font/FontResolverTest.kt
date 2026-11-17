package io.bsoft.bconverter.domain.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontResolverTest {
    private fun f(family: String, w: Int = 400, italic: Boolean = false, src: FontSource = FontSource.SYSTEM, variable: Boolean = false) =
        FontFileRef(family, FontVariant(w, italic), "/f/$family-$w${if (italic) "i" else ""}-$src.ttf", src, variable)
    private val r = FontResolver("Roboto")
    private val files = listOf(f("Roboto"), f("Roboto", 700), f("Roboto", 400, true), f("Lobster"), f("Open Sans", 300, src = FontSource.DOWNLOADED), f("Open Sans", 800, src = FontSource.DOWNLOADED))

    @Test fun exact_match_is_exact() { val x = r.resolve(files, "Roboto", 700, false); assertEquals("/f/Roboto-700-SYSTEM.ttf", x.file!!.path); assertTrue(x.exact) }
    @Test fun family_names_ignore_case_and_spaces() = assertEquals("Open Sans", r.resolve(files, "open sans", 300, false).usedFamily)
    @Test fun the_closest_weight_is_used_and_flagged_inexact() {
        val x = r.resolve(files, "Open Sans", 700, false)
        assertEquals(800, x.file!!.variant.weight); assertFalse(x.exact); assertEquals("Open Sans", x.usedFamily)
    }
    @Test fun the_requested_slant_wins_over_a_closer_weight() = assertTrue(r.resolve(files, "Roboto", 700, true).file!!.variant.italic)
    @Test fun an_italic_request_without_italics_falls_back_to_upright_inexact() { val x = r.resolve(files, "Lobster", 400, true); assertFalse(x.file!!.variant.italic); assertFalse(x.exact) }
    @Test fun a_missing_family_falls_back_to_roboto_and_says_so() {
        val x = r.resolve(files, "Nonexistent", 400, false)
        assertEquals("Roboto", x.usedFamily); assertEquals("Nonexistent", x.requestedFamily); assertFalse(x.exact)
    }
    @Test fun no_family_means_the_default_and_counts_as_exact() { val x = r.resolve(files, null, 400, false); assertEquals("Roboto", x.usedFamily); assertTrue(x.exact) }
    @Test fun blank_family_is_the_same_as_none() = assertEquals("Roboto", r.resolve(files, "  ", 400, false).usedFamily)
    @Test fun a_variable_font_covers_every_weight() {
        val v = listOf(f("Inter", 400, variable = true))
        val x = r.resolve(v, "Inter", 650, false); assertTrue(x.exact); assertEquals("Inter", x.usedFamily)
    }
    @Test fun a_downloaded_font_beats_a_system_font_of_the_same_style() {
        val both = listOf(f("Lobster"), f("Lobster", src = FontSource.DOWNLOADED))
        assertEquals(FontSource.DOWNLOADED, r.resolve(both, "Lobster", 400, false).file!!.source)
    }
    @Test fun without_roboto_any_installed_font_is_used() = assertEquals("Lobster", r.resolve(listOf(f("Lobster")), "Missing", 400, false).usedFamily)
    @Test fun with_no_fonts_at_all_the_file_is_null() { val x = r.resolve(emptyList(), "A", 400, false); assertNull(x.file); assertFalse(x.exact) }
    @Test fun weights_outside_the_scale_are_clamped() = assertEquals(700, r.resolve(files, "Roboto", 5000, false).file!!.variant.weight)
}
