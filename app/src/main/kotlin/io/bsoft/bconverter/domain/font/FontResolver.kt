package io.bsoft.bconverter.domain.font

import kotlin.math.abs

/**
 * Picks the font file for a family, weight and slant out of everything installed. Order of preference:
 * the requested family (downloaded before system) at the exact weight, then at the closest weight (same slant first);
 * if the family is not installed, the fallback family the same way.
 */
class FontResolver(private val fallbackFamily: String = "Roboto") {

    fun resolve(files: List<FontFileRef>, family: String?, weight: Int, italic: Boolean): ResolvedFont {
        val w = weight.coerceIn(100, 900)
        val requested = family?.takeIf { it.isNotBlank() }
        if (requested != null) {
            pick(files, requested, w, italic)?.let { (file, exact) -> return ResolvedFont(file, requested, file.family, exact) }
        }
        pick(files, fallbackFamily, w, italic)?.let { (file, _) -> return ResolvedFont(file, requested, file.family, requested == null) }
        val any = files.filter { !it.variant.italic || italic }.minByOrNull { abs(it.variant.weight - w) } ?: files.firstOrNull()
        return ResolvedFont(any, requested, any?.family, false)
    }

    private fun pick(files: List<FontFileRef>, family: String, weight: Int, italic: Boolean): Pair<FontFileRef, Boolean>? {
        val key = FontNames.key(family)
        val pool = files.filter { FontNames.key(it.family) == key }
        if (pool.isEmpty()) return null
        pool.firstOrNull { it.isVariable && it.variant.italic == italic }?.let { return it to true }
        val rank = compareBy<FontFileRef>({ if (it.variant.italic == italic) 0 else 1 }, { abs(it.variant.weight - weight) }, { if (it.source == FontSource.DOWNLOADED) 0 else 1 })
        val best = pool.sortedWith(rank).first()
        return best to (best.variant.weight == weight && best.variant.italic == italic)
    }
}
