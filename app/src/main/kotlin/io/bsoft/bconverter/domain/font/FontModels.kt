package io.bsoft.bconverter.domain.font

enum class FontSource { SYSTEM, DOWNLOADED }

/** Licences of the fonts the app can fetch. Every Google Fonts family is open source; the catalog says which licence when it knows. */
enum class FontLicense(val label: String) {
    OFL("SIL Open Font License 1.1"),
    APACHE("Apache License 2.0"),
    UBUNTU("Ubuntu Font License 1.0"),
    OPEN_SOURCE("Open source (Google Fonts)"),
    SYSTEM("Installed with the device");

    companion object {
        fun fromCatalog(value: String?): FontLicense = when (value?.trim()?.lowercase()) {
            "ofl", "sil open font license", "open font license" -> OFL
            "apache2", "apache", "apache 2.0" -> APACHE
            "ufl", "ubuntu" -> UBUNTU
            else -> OPEN_SOURCE
        }
        fun fromName(name: String?): FontLicense = entries.firstOrNull { it.name == name } ?: OPEN_SOURCE
    }
}

/** One weight (CSS scale 100..900) and slant of a family. */
data class FontVariant(val weight: Int, val italic: Boolean = false) {
    init { require(weight in 100..900) { "weight $weight" } }
    /** The Google Fonts key form: "400", "700i". */
    val key: String get() = "$weight" + if (italic) "i" else ""

    companion object {
        fun fromKey(key: String): FontVariant? {
            val italic = key.endsWith("i")
            val w = key.removeSuffix("i").toIntOrNull() ?: return null
            return if (w in 100..900) FontVariant(w, italic) else null
        }
    }
}

/** A family in the Google Fonts catalog. [popularity] is a rank (1 = most used); 0 when unknown. */
data class CatalogFont(
    val family: String,
    val category: String,
    val variants: List<FontVariant>,
    val license: FontLicense = FontLicense.OPEN_SOURCE,
    val popularity: Int = 0,
)

/** A font file on this device. A variable font covers every weight in 100..900 from one file. */
data class FontFileRef(
    val family: String,
    val variant: FontVariant,
    val path: String,
    val source: FontSource,
    val isVariable: Boolean = false,
    val license: FontLicense = if (source == FontSource.SYSTEM) FontLicense.SYSTEM else FontLicense.OPEN_SOURCE,
)

/**
 * The answer to "which file draws this family at this weight?". [exact] is false when the closest weight or a stand-in family
 * was used, which the editor can tell the user; [file] is null only if the device has no usable font at all.
 */
data class ResolvedFont(val file: FontFileRef?, val requestedFamily: String?, val usedFamily: String?, val exact: Boolean)

object FontNames {
    /** Case, spaces and punctuation do not matter when comparing family names. */
    fun key(family: String): String = family.lowercase().filter { it.isLetterOrDigit() }
}
