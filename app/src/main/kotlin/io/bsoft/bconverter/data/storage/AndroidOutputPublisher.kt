package io.bsoft.bconverter.data.storage

import android.content.ContentResolver
import android.net.Uri
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.repository.PublishResult
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Streams the file to the destination in chunks (never loads it into memory). */
class AndroidOutputPublisher(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) : OutputPublisher {
    override suspend fun publish(source: File, destination: Uri): PublishResult = withContext(ioDispatcher) {
        try {
            val output = contentResolver.openOutputStream(destination, "w") ?: return@withContext PublishResult.Failure
            output.use { stream ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        stream.write(buffer, 0, read)
                    }
                }
            }
            PublishResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            io.bsoft.bconverter.core.diagnostics.AppLog.nonFatal("Publish", "Copying the result to its destination failed", e)
            PublishResult.Failure
        }
    }

    private companion object { const val BUFFER_BYTES = 64 * 1024 }
}
