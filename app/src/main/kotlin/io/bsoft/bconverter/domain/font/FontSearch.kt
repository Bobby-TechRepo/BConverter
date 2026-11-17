package io.bsoft.bconverter.domain.font

object FontSearch {
    /**
     * Families matching [query] (name only), best first: exact, then prefix, then a word starting with it, then containing it;
     * ties by popularity. A blank query lists everything by popularity. [category] narrows to one category (e.g. "serif").
     */
    fun search(fonts: List<CatalogFont>, query: String, category: String? = null, limit: Int = 100): List<CatalogFont> {
        val q = query.trim().lowercase()
        val qk = FontNames.key(q)
        val pool = fonts.filter { category == null || it.category.equals(category, ignoreCase = true) }
        val ranked = if (q.isEmpty()) pool.map { it to 0 } else pool.mapNotNull { f ->
            val name = f.family.lowercase()
            val score = when {
                name == q || FontNames.key(name) == qk -> 0
                name.startsWith(q) -> 1
                name.split(' ', '-').any { it.startsWith(q) } -> 2
                name.contains(q) || FontNames.key(name).contains(qk) -> 3
                else -> return@mapNotNull null
            }
            f to score
        }
        return ranked.sortedWith(compareBy({ it.second }, { if (it.first.popularity > 0) it.first.popularity else Int.MAX_VALUE }, { it.first.family })).map { it.first }.take(limit)
    }

    fun categories(fonts: List<CatalogFont>): List<String> = fonts.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
}
