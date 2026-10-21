package io.bsoft.bconverter.data.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import io.bsoft.bconverter.domain.repository.UriPermissionPersister

class AndroidUriPermissionPersister(private val contentResolver: ContentResolver) : UriPermissionPersister {
    override fun persist(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Not every provider offers persistable grants (e.g. one-off picker results); the session still works.
        } catch (_: IllegalArgumentException) {
        }
    }
}
