package io.bsoft.bconverter.domain.font

import org.json.JSONObject

/** Reads the Google Fonts catalog (the JSON behind fonts.google.com) and the CSS used to find a font's download links. */
object GoogleFonts {
    const val METADATA_URL = "https://fonts.google.com/metadata/fonts"

    /** Empty when the text is not the expected JSON, so a bad response never crashes the screen. */
    fun parseCatalog(text: String): List<CatalogFont> = try {
        val body = text.trimStart().let { if (it.startsWith(")]}'")) it.removePrefix(")]}'") else it }
        val list = JSONObject(body).getJSONArray("familyMetadataList")
        (0 until list.length()).mapNotNull { i ->
            val o = list.getJSONObject(i)
            val family = o.optString("family").ifBlank { return@mapNotNull null }
            val fonts = o.optJSONObject("fonts")
            val variants = fonts?.keys()?.asSequence()?.mapNotNull { FontVariant.fromKey(it) }?.sortedWith(compareBy({ it.italic }, { it.weight }))?.toList().orEmpty()
            if (variants.isEmpty()) return@mapNotNull null
            CatalogFont(family, o.optString("category").lowercase().replace('_', ' '), variants, FontLicense.fromCatalog(o.optString("license").ifEmpty { null }), o.optInt("popularity", 0))
        }
    } catch (e: Exception) { emptyList() }

    /** `https://fonts.googleapis.com/css2?family=Open+Sans:ital,wght@0,400;0,700;1,400` */
    fun cssUrl(family: String, variants: List<FontVariant>): String {
        require(variants.isNotEmpty()) { "no variants" }
        val tuples = variants.distinct().sortedWith(compareBy({ it.italic }, { it.weight })).joinToString(";") { "${if (it.italic) 1 else 0},${it.weight}" }
        return "https://fonts.googleapis.com/css2?family=${family.trim().replace(' ', '+')}:ital,wght@$tuples"
    }

    data class CssFont(val variant: FontVariant, val url: String)

    private val block = Regex("@font-face\\s*\\{([^}]*)\\}")
    private val styleRx = Regex("font-style:\\s*(\\w+)")
    private val weightRx = Regex("font-weight:\\s*(\\d+)")
    private val urlRx = Regex("url\\(\\s*['\"]?([^)'\"\\s]+)['\"]?\\s*\\)")

    fun parseCss(css: String): List<CssFont> = block.findAll(css).mapNotNull { m ->
        val b = m.groupValues[1]
        val w = weightRx.find(b)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 100..900 } ?: return@mapNotNull null
        val url = urlRx.find(b)?.groupValues?.get(1) ?: return@mapNotNull null
        CssFont(FontVariant(w, styleRx.find(b)?.groupValues?.get(1) == "italic"), url)
    }.distinctBy { it.variant }.toList()

    /** Only Google's own HTTPS hosts are ever contacted, whatever the CSS says. */
    fun isAllowedUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore('/').substringBefore(':').substringAfterLast('@').lowercase()
        return host == "fonts.googleapis.com" || host == "fonts.gstatic.com"
    }
}

object FontFileValidator {
    const val MAX_BYTES = 20 * 1024 * 1024
    private val magics = listOf(byteArrayOf(0, 1, 0, 0), "OTTO".toByteArray(), "true".toByteArray(), "ttcf".toByteArray())

    /** True for TrueType/OpenType data of a sane size. WOFF and HTML error pages are refused. */
    fun isUsableFont(bytes: ByteArray): Boolean = bytes.size in 1024..MAX_BYTES && magics.any { m -> m.indices.all { bytes[it] == m[it] } }
}
