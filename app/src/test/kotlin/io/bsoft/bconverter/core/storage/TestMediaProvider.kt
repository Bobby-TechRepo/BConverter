package io.bsoft.bconverter.core.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns

/** Minimal provider exposing OpenableColumns for AndroidContentReader tests. */
class TestMediaProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = types[uri.path]
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? {
        val row = rows[uri.path] ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .apply { addRow(arrayOf<Any?>(row.first, row.second)) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    companion object {
        val rows = mutableMapOf<String, Pair<String, Long?>>()
        val types = mutableMapOf<String, String>()
    }
}
