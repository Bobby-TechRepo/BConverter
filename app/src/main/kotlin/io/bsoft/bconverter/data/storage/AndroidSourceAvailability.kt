package io.bsoft.bconverter.data.storage

import android.content.ContentResolver
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.project.SourceAvailability

/** A source is available if it can still be opened: the file exists, or the content URI (and its permission grant) still works. */
class AndroidSourceAvailability(private val resolver: ContentResolver) : SourceAvailability {
    override fun isAvailable(source: MediaSource): Boolean = when (source) {
        is MediaSource.FileSource -> source.file.isFile
        is MediaSource.UriSource -> try {
            resolver.openAssetFileDescriptor(source.uri, "r")?.use { true } ?: false
        } catch (_: Exception) { false } // FileNotFound, SecurityException (grant revoked), IllegalArgument, …
    }
}
