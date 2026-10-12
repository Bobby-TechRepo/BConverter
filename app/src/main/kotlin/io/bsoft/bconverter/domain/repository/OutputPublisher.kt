package io.bsoft.bconverter.domain.repository

import android.net.Uri
import java.io.File

sealed interface PublishResult {
    data object Success : PublishResult
    data object Failure : PublishResult
}

/** Copies a finished temp file to the destination the user chose (e.g. a Storage Access Framework document). */
interface OutputPublisher {
    suspend fun publish(source: File, destination: Uri): PublishResult
}
