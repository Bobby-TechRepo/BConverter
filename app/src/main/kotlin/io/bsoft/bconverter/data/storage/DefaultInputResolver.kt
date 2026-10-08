package io.bsoft.bconverter.data.storage

import android.net.Uri
import io.bsoft.bconverter.core.storage.ContentReader
import io.bsoft.bconverter.core.storage.FreeSpaceProvider
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.model.ContentInfo
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.ValidationResult
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.usecase.MediaFileValidator
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DefaultInputResolver(
    private val reader: ContentReader,
    private val staging: StagingDirectory,
    private val freeSpace: FreeSpaceProvider,
    private val ioDispatcher: CoroutineDispatcher,
    private val validator: MediaFileValidator = MediaFileValidator(),
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE_BYTES,
) : InputResolver {

    override suspend fun resolve(source: MediaSource): ResolveResult = withContext(ioDispatcher) {
        when (source) {
            is MediaSource.UriSource -> stageUri(source.uri)
            is MediaSource.FileSource -> useFile(source.file)
        }
    }

    override fun release(input: ResolvedInput) {
        if (input.isStaged) input.file.delete()
    }

    private fun CoroutineScope.stageUri(uri: Uri): ResolveResult {
        val info = try {
            reader.info(uri)
        } catch (e: Exception) {
            return failure(e)
        }
        (validator.validate(info) as? ValidationResult.Invalid)?.let { return ResolveResult.Failure(it.error) }

        info.sizeBytes?.let { required ->
            val available = freeSpace.availableBytes(staging.root)
            if (available < required) {
                return ResolveResult.Failure(InputError.InsufficientStorage(required, available))
            }
        }

        val target = staging.newFile(info.displayName)
        var completed = false
        try {
            reader.open(uri).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            completed = true
            return ResolveResult.Success(
                ResolvedInput(target, info.displayName ?: FALLBACK_NAME, target.length(), isStaged = true),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(e)
        } finally {
            if (!completed) target.delete()
        }
    }

    private fun useFile(file: File): ResolveResult {
        if (!file.exists()) return ResolveResult.Failure(InputError.NotFound)
        if (!file.isFile || !file.canRead()) return ResolveResult.Failure(InputError.Unreadable)
        val info = ContentInfo(file.name, mimeType = null, sizeBytes = file.length())
        (validator.validate(info) as? ValidationResult.Invalid)?.let { return ResolveResult.Failure(it.error) }
        return ResolveResult.Success(ResolvedInput(file, file.name, file.length(), isStaged = false))
    }

    private fun failure(e: Throwable) = ResolveResult.Failure(
        when (e) {
            is SecurityException -> InputError.PermissionDenied
            is FileNotFoundException -> InputError.NotFound
            else -> InputError.Unreadable
        },
    )

    private companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024
        const val FALLBACK_NAME = "input"
    }
}
