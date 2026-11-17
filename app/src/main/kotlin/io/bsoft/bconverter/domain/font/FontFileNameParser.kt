package io.bsoft.bconverter.domain.font

/** What a font file's name tells about it. */
data class ParsedFontName(val family: String, val variant: FontVariant, val isVariable: Boolean)

/** Reads Android's system font naming, e.g. `Roboto-BoldItalic.ttf`, `NotoSerif-Regular.ttf`, `Roboto[wdth,wght].ttf`. */
object FontFileNameParser {
    private val extensions = setOf("ttf", "otf", "ttc")
    private val skip = listOf("emoji", "symbol", "flag")
    private val camel = Regex("(?<=[a-z0-9])(?=[A-Z])")

    private val weights = listOf(
        "extralight" to 200, "ultralight" to 200, "semibold" to 600, "demibold" to 600, "extrabold" to 800, "ultrabold" to 800,
        "thin" to 100, "light" to 300, "regular" to 400, "normal" to 400, "book" to 400, "medium" to 500, "bold" to 700,
        "black" to 900, "heavy" to 900,
    )

    fun parse(fileName: String): ParsedFontName? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in extensions) return null
        var stem = fileName.substringBeforeLast('.')
        if (skip.any { stem.lowercase().contains(it) }) return null
        val variable = stem.contains('[')
        if (variable) stem = stem.substringBefore('[').trimEnd('-')
        val familyPart = stem.substringBefore('-')
        val style = if (stem.contains('-')) stem.substringAfter('-') else ""
        if (familyPart.isBlank()) return null
        val s = style.lowercase()
        val italic = s.contains("italic") || s.contains("oblique")
        val weight = weights.firstOrNull { s.contains(it.first) }?.second ?: 400
        val family = familyPart.replace(camel, " ").replace('_', ' ').trim()
        return ParsedFontName(family, FontVariant(weight, italic), variable)
    }
}
