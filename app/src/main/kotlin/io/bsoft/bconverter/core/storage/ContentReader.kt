package io.bsoft.bconverter.core.storage

import android.net.Uri
import io.bsoft.bconverter.domain.model.ContentInfo
import java.io.InputStream

/**
 * Android-facing seam for reading content URIs.
 * Implementations throw [java.io.FileNotFoundException], [SecurityException] or [java.io.IOException].
 */
interface ContentReader {
    fun info(uri: Uri): ContentInfo
    fun open(uri: Uri): InputStream
}
