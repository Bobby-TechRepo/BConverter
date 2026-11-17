package io.bsoft.bconverter.data.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import io.bsoft.bconverter.domain.project.UriGrantCleanup

/**
 * Releases persisted read grants that no saved project uses (see [UriGrantCleanup]). Run at app start, when no editor
 * is open, so a picked-but-unsaved clip is never affected. Returns how many grants were released; failures are skipped.
 */
class AndroidUriGrantCleaner(
    private val resolver: ContentResolver,
    private val savedProjects: suspend () -> List<String>,
) {
    suspend fun clean(): Int {
        val granted = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }
        if (granted.isEmpty()) return 0
        return UriGrantCleanup.unused(granted, savedProjects()).count { uri ->
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
        }
    }
}
