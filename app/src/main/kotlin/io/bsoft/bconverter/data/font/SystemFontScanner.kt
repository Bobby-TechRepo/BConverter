package io.bsoft.bconverter.data.font

import io.bsoft.bconverter.domain.font.FontFileNameParser
import io.bsoft.bconverter.domain.font.FontFileRef
import io.bsoft.bconverter.domain.font.FontSource
import java.io.File

/** Lists the fonts the device ships with, read from the file names in the system font folders. */
class SystemFontScanner(private val dirs: List<File> = DEFAULT_DIRS.map(::File)) {
    fun scan(): List<FontFileRef> = dirs.flatMap { dir ->
        dir.listFiles().orEmpty().filter { it.isFile }.mapNotNull { f ->
            FontFileNameParser.parse(f.name)?.let { FontFileRef(it.family, it.variant, f.path, FontSource.SYSTEM, it.isVariable) }
        }
    }.distinctBy { Triple(it.family.lowercase(), it.variant, it.isVariable) }

    companion object { val DEFAULT_DIRS = listOf("/system/fonts", "/system/product/fonts", "/system_ext/fonts", "/product/fonts") }
}
