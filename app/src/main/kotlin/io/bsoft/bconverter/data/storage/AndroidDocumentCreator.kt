package io.bsoft.bconverter.data.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.bsoft.bconverter.domain.repository.DocumentCreator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class AndroidDocumentCreator(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) : DocumentCreator {
    override suspend fun create(folder: Uri, fileName: String, mimeType: String): Uri? = withContext(ioDispatcher) {
        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            DocumentsContract.createDocument(contentResolver, parent, mimeType, fileName)
        } catch (e: Exception) {
            null
        }
    }
}
