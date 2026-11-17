package io.bsoft.bconverter.domain.repository

import android.net.Uri

/** Creates an empty file inside a folder the user picked; returns its Uri, or null if that failed. */
interface DocumentCreator {
    suspend fun create(folder: Uri, fileName: String, mimeType: String): Uri?
}
