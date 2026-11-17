package io.bsoft.bconverter.data.storage

import io.bsoft.bconverter.domain.media.ThumbnailStore
import java.io.File
import java.security.MessageDigest

/** Thumbnails as small JPEG files in a cache directory, named by a hash of their key, trimmed oldest-first to a size budget. */
class DiskThumbnailStore(private val dir: File, private val maxBytes: Long) : ThumbnailStore {
    override fun fileFor(key: String): File {
        dir.mkdirs()
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.jpg")
    }

    /** Deletes the least recently modified files until the directory fits the budget; returns how many were removed. */
    fun trim(): Int {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return 0
        var total = files.sumOf { it.length() }
        var removed = 0
        for (f in files) {
            if (total <= maxBytes) break
            val size = f.length()
            if (f.delete()) { total -= size; removed++ }
        }
        return removed
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}
