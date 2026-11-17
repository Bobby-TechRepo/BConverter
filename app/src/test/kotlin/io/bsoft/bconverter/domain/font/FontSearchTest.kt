package io.bsoft.bconverter.domain.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontSearchTest {
    private fun c(n: String, cat: String = "sans serif", pop: Int = 0) = CatalogFont(n, cat, listOf(FontVariant(400)), popularity = pop)
    private val all = listOf(c("Open Sans", pop = 2), c("Sans Forgetica", pop = 50), c("Roboto", pop = 1), c("Roboto Slab", "serif", 3), c("Lobster", "display", 5), c("Noto Sans", pop = 4))

    @Test fun a_blank_query_lists_by_popularity() = assertEquals(listOf("Roboto", "Open Sans", "Roboto Slab", "Noto Sans", "Lobster", "Sans Forgetica"), FontSearch.search(all, "").map { it.family })
    @Test fun exact_then_prefix_then_word_then_contains() {
        assertEquals(listOf("Roboto", "Roboto Slab"), FontSearch.search(all, "roboto").map { it.family })
        assertEquals(listOf("Sans Forgetica", "Open Sans", "Noto Sans"), FontSearch.search(all, "sans").map { it.family })
    }
    @Test fun search_ignores_spaces_and_case() = assertEquals("Open Sans", FontSearch.search(all, "OPENSANS").first().family)
    @Test fun substring_matches_come_last() = assertEquals(listOf("Lobster"), FontSearch.search(all, "obst").map { it.family })
    @Test fun nothing_matching_is_empty() = assertTrue(FontSearch.search(all, "zzz").isEmpty())
    @Test fun category_narrows_the_result() = assertEquals(listOf("Roboto Slab"), FontSearch.search(all, "", "serif").map { it.family })
    @Test fun the_limit_is_applied() = assertEquals(2, FontSearch.search(all, "", limit = 2).size)
    @Test fun categories_are_distinct_and_sorted() = assertEquals(listOf("display", "sans serif", "serif"), FontSearch.categories(all))
}
