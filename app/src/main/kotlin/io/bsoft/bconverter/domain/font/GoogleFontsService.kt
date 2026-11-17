package io.bsoft.bconverter.domain.font

import java.io.File
import java.io.IOException

/** The network, as the font code needs it. Implementations must only throw [IOException]. */
interface FontHttp {
    suspend fun getText(url: String): String
    suspend fun getBytes(url: String, maxBytes: Int): ByteArray
}

sealed interface CatalogResult {
    data class Ready(val fonts: List<CatalogFont>, val fromCacheOnly: Boolean) : CatalogResult
    data object Unavailable : CatalogResult
}

/** The font list: fetched at most once a week, kept on disk, and still usable offline from the last copy. */
class GoogleFontsCatalog(
    private val http: FontHttp,
    private val cacheFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = 7L * 24 * 3600 * 1000,
) {
    suspend fun load(forceRefresh: Boolean = false): CatalogResult {
        val cached = cacheFile.takeIf { it.isFile }?.let { runCatching { GoogleFonts.parseCatalog(it.readText()) }.getOrDefault(emptyList()) }.orEmpty()
        val fresh = cached.isNotEmpty() && clock() - cacheFile.lastModified() < maxAgeMs
        if (fresh && !forceRefresh) return CatalogResult.Ready(cached, fromCacheOnly = false)
        return try {
            val text = http.getText(GoogleFonts.METADATA_URL)
            val parsed = GoogleFonts.parseCatalog(text)
            if (parsed.isEmpty()) throw IOException("empty catalog")
            cacheFile.parentFile?.mkdirs(); cacheFile.writeText(text)
            CatalogResult.Ready(parsed, fromCacheOnly = false)
        } catch (e: IOException) {
            if (cached.isNotEmpty()) CatalogResult.Ready(cached, fromCacheOnly = true) else CatalogResult.Unavailable
        }
    }
}

sealed interface FontDownload {
    data class Done(val stored: List<FontVariant>) : FontDownload
    data object Offline : FontDownload
    data object BadResponse : FontDownload
    data object NothingToDownload : FontDownload
}

/** Downloads chosen variants of a catalog family into the cache. Nothing is downloaded unless the user asks. */
class FontDownloader(private val http: FontHttp, private val store: FontCacheStore) {
    suspend fun download(font: CatalogFont, wanted: List<FontVariant>): FontDownload {
        val variants = wanted.filter { it in font.variants }.distinct()
        if (variants.isEmpty()) return FontDownload.NothingToDownload
        val css = try { http.getText(GoogleFonts.cssUrl(font.family, variants)) } catch (e: IOException) { return FontDownload.Offline }
        val links = GoogleFonts.parseCss(css).filter { it.variant in variants && GoogleFonts.isAllowedUrl(it.url) }
        if (links.isEmpty()) return FontDownload.BadResponse
        val stored = mutableListOf<FontVariant>()
        for (link in links) {
            val bytes = try { http.getBytes(link.url, FontFileValidator.MAX_BYTES) } catch (e: IOException) { return if (stored.isEmpty()) FontDownload.Offline else FontDownload.Done(stored) }
            if (store.put(font.family, link.variant, font.license, bytes)) stored += link.variant
        }
        return if (stored.isEmpty()) FontDownload.BadResponse else FontDownload.Done(stored)
    }

    /** Regular weight if the family has it, else its first variant: what a quick preview needs. */
    fun previewVariant(font: CatalogFont): FontVariant = font.variants.firstOrNull { it.weight == 400 && !it.italic } ?: font.variants.first()
}
