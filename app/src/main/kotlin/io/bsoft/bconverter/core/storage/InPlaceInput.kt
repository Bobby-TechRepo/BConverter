package io.bsoft.bconverter.core.storage

import android.net.Uri
import java.io.Closeable
import java.io.File

/** A readable path to a picked document's own bytes (no copy), valid until [close]. */
interface InPlaceHandle : Closeable {
    val file: File
}

/**
 * Seam for reading a picked document in place instead of copying it (implementation plan step 15). Returns null when that
 * is not possible (not a seekable local file, or not reopenable from this process); the caller then copies as before.
 * [link] is a fresh, unique path inside the staging directory that the handle may occupy.
 */
fun interface InPlaceOpener {
    fun open(uri: Uri, link: File): InPlaceHandle?
}
