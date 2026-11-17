package io.bsoft.bconverter.domain.font

/** One row of the installed-fonts list. [sample] is the file used to show the name in its own typeface. */
data class InstalledFamily(val name: String, val variants: List<FontVariant>, val downloaded: Boolean, val license: FontLicense, val sample: FontFileRef?)

/** Everything the rest of the app asks about fonts: what is installed, and which file draws a given style. */
class FontLibrary(
    private val system: () -> List<FontFileRef>,
    private val downloaded: () -> List<FontFileRef>,
    private val recents: RecentFonts,
    private val resolver: FontResolver = FontResolver(),
) {
    private val systemFiles by lazy(system)

    fun installedFiles(): List<FontFileRef> = downloaded() + systemFiles

    /** Distinct installed families, sorted by name. */
    fun families(): List<String> = installedFiles().map { it.family }.distinctBy(FontNames::key).sortedBy { it.lowercase() }

    fun installedFamilies(): List<InstalledFamily> {
        val files = installedFiles()
        return families().map { name ->
            val own = files.filter { FontNames.key(it.family) == FontNames.key(name) }
            val downloaded = own.any { it.source == FontSource.DOWNLOADED }
            InstalledFamily(name, variantsOf(name), downloaded, own.first { !downloaded || it.source == FontSource.DOWNLOADED }.license, resolve(name, 400, false).file)
        }
    }

    fun variantsOf(family: String): List<FontVariant> = installedFiles().filter { FontNames.key(it.family) == FontNames.key(family) }.map { it.variant }.distinct().sortedWith(compareBy({ it.italic }, { it.weight }))

    fun recentFamilies(): List<String> = recents.list().filter { r -> installedFiles().any { FontNames.key(it.family) == FontNames.key(r) } }

    fun markUsed(family: String) = recents.add(family)

    /** The file FFmpeg should draw with (drawtext `fontfile`) for this style; [ResolvedFont.exact] is false when a stand-in was used. */
    fun resolve(family: String?, weight: Int = 400, italic: Boolean = false): ResolvedFont = resolver.resolve(installedFiles(), family, weight, italic)
}
