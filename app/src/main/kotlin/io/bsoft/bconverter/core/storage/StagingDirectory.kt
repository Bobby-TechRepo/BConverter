package io.bsoft.bconverter.core.storage

import java.io.File
import java.util.UUID

/** App-controlled directory for temporary copies of inaccessible inputs. */
class StagingDirectory(val root: File) {
    init { root.mkdirs() }

    /** Reserves a unique, sanitised path inside [root]; the file is not created. */
    fun newFile(displayName: String?): File = File(root, "${UUID.randomUUID()}_${sanitize(displayName)}")

    fun listFiles(): List<File> = root.listFiles()?.toList().orEmpty()

    fun clear() { listFiles().forEach { it.deleteRecursively() } }

    fun clearOlderThan(cutoffMs: Long) {
        listFiles().filter { it.lastModified() < cutoffMs }.forEach { it.deleteRecursively() }
    }

    private fun sanitize(displayName: String?): String {
        if (displayName.isNullOrBlank()) return FALLBACK_NAME
        val leaf = displayName.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = leaf.replace(UNSAFE, "_").trim('.')
        if (cleaned.isEmpty()) return FALLBACK_NAME
        if (cleaned.length <= MAX_NAME_LENGTH) return cleaned
        val extension = cleaned.substringAfterLast('.', "")
        if (extension.isEmpty() || extension.length > MAX_EXTENSION_LENGTH) return cleaned.take(MAX_NAME_LENGTH)
        val stem = cleaned.removeSuffix(".$extension")
        return stem.take(MAX_NAME_LENGTH - extension.length - 1) + "." + extension
    }

    private companion object {
        const val FALLBACK_NAME = "input"
        const val MAX_NAME_LENGTH = 100
        const val MAX_EXTENSION_LENGTH = 10
        val UNSAFE = Regex("[^A-Za-z0-9._-]")
    }
}
