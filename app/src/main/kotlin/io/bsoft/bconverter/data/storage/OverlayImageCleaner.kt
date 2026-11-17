package io.bsoft.bconverter.data.storage

import io.bsoft.bconverter.domain.overlay.OverlayImageCleanup
import io.bsoft.bconverter.domain.overlay.StoredImage
import java.io.File

/** Deletes imported overlay pictures that no saved project uses (see [OverlayImageCleanup]). Returns how many files were removed. */
/** [savedProjects] returns every saved project's serialized text. */
class OverlayImageCleaner(private val savedProjects: suspend () -> List<String>, private val dir: File, private val nowMs: () -> Long) {
    suspend fun clean(): Int {
        val files = dir.listFiles()?.filter { it.isFile }?.map { StoredImage(it.name, it.lastModified()) } ?: return 0
        if (files.isEmpty()) return 0
        val unused = OverlayImageCleanup.unused(files, savedProjects(), nowMs())
        return unused.count { File(dir, it).delete() }
    }
}
