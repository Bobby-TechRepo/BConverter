package io.bsoft.bconverter.domain.settings

import java.io.File

interface CacheStorage {
    fun usageBytes(): Long

    /** Deletes everything regenerable; returns the bytes freed. */
    fun clear(): Long
}

/** Measures and empties regenerable cache folders (previews, waveforms). The folders themselves are kept. */
class CacheManager(private val folders: List<File>) : CacheStorage {
    override fun usageBytes(): Long = folders.sumOf { sizeOf(it) }

    /** Deletes everything inside the folders; returns the bytes actually freed. */
    override fun clear(): Long {
        var freed = 0L
        folders.forEach { folder ->
            folder.listFiles()?.forEach { child ->
                val size = sizeOf(child)
                if (child.deleteRecursively()) freed += size
            }
        }
        return freed
    }

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
}
