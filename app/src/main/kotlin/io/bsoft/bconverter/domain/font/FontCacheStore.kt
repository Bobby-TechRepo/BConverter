package io.bsoft.bconverter.domain.font

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Downloaded fonts: the files live flat in [fontsDir] (the folder FFmpeg reads fonts from) with a `gf-` prefix, and a small
 * index file remembers family, variant, licence and last use. Writes go through a temp file so a crash never leaves a half font.
 */
class FontCacheStore(private val fontsDir: File, private val indexFile: File, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val family: String, val variant: FontVariant, val file: String, val license: FontLicense, val usedAt: Long, val variable: Boolean)

    private fun load(): List<Entry> = try {
        val a = JSONArray(indexFile.readText())
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val e = Entry(o.getString("family"), FontVariant(o.getInt("weight"), o.optBoolean("italic", false)), o.getString("file"), FontLicense.fromName(o.optString("license")), o.optLong("usedAt", 0), o.optBoolean("variable", false))
            e.takeIf { File(fontsDir, it.file).isFile }
        }
    } catch (e: Exception) { emptyList() }

    private fun save(entries: List<Entry>) {
        indexFile.parentFile?.mkdirs()
        val a = JSONArray().also { arr -> entries.forEach { e -> arr.put(JSONObject().put("family", e.family).put("weight", e.variant.weight).put("italic", e.variant.italic).put("file", e.file).put("license", e.license.name).put("usedAt", e.usedAt).put("variable", e.variable)) } }
        val tmp = File(indexFile.path + ".part")
        tmp.writeText(a.toString()); tmp.renameTo(indexFile)
    }

    fun list(): List<FontFileRef> = load().map { FontFileRef(it.family, it.variant, File(fontsDir, it.file).path, FontSource.DOWNLOADED, it.variable, it.license) }

    fun has(family: String, variant: FontVariant): Boolean = load().any { FontNames.key(it.family) == FontNames.key(family) && it.variant == variant }

    /** Stores (or replaces) one variant. The bytes must be a real font; returns false otherwise. */
    fun put(family: String, variant: FontVariant, license: FontLicense, bytes: ByteArray): Boolean {
        if (!FontFileValidator.isUsableFont(bytes)) return false
        fontsDir.mkdirs()
        val name = "gf-" + FontNames.key(family).ifEmpty { "font" } + "-${variant.key}.ttf"
        val tmp = File(fontsDir, "$name.part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(fontsDir, name)) && !(File(fontsDir, name).delete() && tmp.renameTo(File(fontsDir, name)))) { tmp.delete(); return false }
        save(load().filterNot { it.file == name } + Entry(family.trim(), variant, name, license, clock(), false))
        return true
    }

    /** Marks a family as just used so it is the last to be removed when space is needed. */
    fun touch(family: String) {
        val k = FontNames.key(family)
        val all = load()
        if (all.none { FontNames.key(it.family) == k }) return
        save(all.map { if (FontNames.key(it.family) == k) it.copy(usedAt = clock()) else it })
    }

    fun remove(family: String) {
        val k = FontNames.key(family)
        val (gone, keep) = load().partition { FontNames.key(it.family) == k }
        gone.forEach { File(fontsDir, it.file).delete() }
        save(keep)
    }

    fun totalBytes(): Long = load().sumOf { File(fontsDir, it.file).length() }

    /** Deletes least-recently-used families until the total is within [maxBytes]; [keep] families are never deleted. Returns the families removed. */
    fun trim(maxBytes: Long, keep: Set<String> = emptySet()): List<String> {
        val protectedKeys = keep.map(FontNames::key).toSet()
        val removed = mutableListOf<String>()
        var entries = load()
        var total = entries.sumOf { File(fontsDir, it.file).length() }
        val order = entries.groupBy { FontNames.key(it.family) }.filterKeys { it !in protectedKeys }.entries.sortedBy { (_, v) -> v.maxOf { it.usedAt } }
        for ((key, group) in order) {
            if (total <= maxBytes) break
            group.forEach { total -= File(fontsDir, it.file).length(); File(fontsDir, it.file).delete() }
            removed += group.first().family
            entries = entries.filterNot { FontNames.key(it.family) == key }
        }
        if (removed.isNotEmpty()) save(entries)
        return removed
    }

    fun clear() { load().forEach { File(fontsDir, it.file).delete() }; save(emptyList()) }
}
