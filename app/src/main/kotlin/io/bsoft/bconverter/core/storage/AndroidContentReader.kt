package io.bsoft.bconverter.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.bsoft.bconverter.domain.model.ContentInfo
import java.io.FileNotFoundException
import java.io.InputStream

class AndroidContentReader(private val contentResolver: ContentResolver) : ContentReader {
    override fun info(uri: Uri): ContentInfo {
        var name: String? = null
        var size: Long? = null
        contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
            }
        }
        return ContentInfo(name ?: uri.lastPathSegment, contentResolver.getType(uri), size)
    }

    override fun open(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
}
