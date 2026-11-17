package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.core.storage.ContentReader
import io.bsoft.bconverter.domain.subtitle.SubtitleCodecs
import io.bsoft.bconverter.domain.subtitle.SubtitleError
import io.bsoft.bconverter.domain.subtitle.SubtitleParseResult
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import io.bsoft.bconverter.domain.subtitle.SubtitleWarning
import io.bsoft.bconverter.domain.timeline.IdGenerator
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

sealed interface SubtitleImportFailure {
    data class Parse(val error: SubtitleError) : SubtitleImportFailure
    data object Unreadable : SubtitleImportFailure
    data object TooLarge : SubtitleImportFailure
}

sealed interface SubtitleImportResult {
    data class Imported(val track: SubtitleTrack, val warnings: List<SubtitleWarning>) : SubtitleImportResult
    data class Failed(val reason: SubtitleImportFailure) : SubtitleImportResult
}

/** Reads a subtitle file the user picked into a [SubtitleTrack]: size limit, text encoding, format detection. */
class SubtitleImporter(
    private val reader: ContentReader,
    private val ids: IdGenerator,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) {
    /** [import] on the I/O dispatcher, for callers on the main thread. */
    suspend fun load(uri: Uri): SubtitleImportResult = kotlinx.coroutines.withContext(ioDispatcher) { import(uri) }

    fun import(uri: Uri): SubtitleImportResult {
        val info = try { reader.info(uri) } catch (e: Exception) { return SubtitleImportResult.Failed(SubtitleImportFailure.Unreadable) }
        if ((info.sizeBytes ?: 0L) > MAX_BYTES) return SubtitleImportResult.Failed(SubtitleImportFailure.TooLarge)
        val bytes = try { readLimited(uri) ?: return SubtitleImportResult.Failed(SubtitleImportFailure.TooLarge) }
        catch (e: Exception) { return SubtitleImportResult.Failed(SubtitleImportFailure.Unreadable) }

        val fileName = info.displayName
        val name = fileName?.substringBeforeLast('.', fileName)?.ifBlank { null } ?: "Subtitles"
        return when (val parsed = SubtitleCodecs.parse(decode(bytes), fileName = fileName, name = name, ids = ids)) {
            is SubtitleParseResult.Failed -> SubtitleImportResult.Failed(SubtitleImportFailure.Parse(parsed.error))
            is SubtitleParseResult.Parsed -> SubtitleImportResult.Imported(parsed.track.copy(id = ids.next()), parsed.warnings)
        }
    }

    /** Null when the stream is longer than [MAX_BYTES]. */
    private fun readLimited(uri: Uri): ByteArray? = reader.open(uri).use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        out.toByteArray()
    }

    /** BOM first, then strict UTF-8, then Latin-1 (every byte maps to a character, so this never fails). */
    private fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, Charsets.ISO_8859_1)
        }
    }

    private companion object { const val MAX_BYTES = 5L * 1024 * 1024 }
}
