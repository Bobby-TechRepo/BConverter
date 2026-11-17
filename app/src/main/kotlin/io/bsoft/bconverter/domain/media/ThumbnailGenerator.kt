package io.bsoft.bconverter.domain.media

import java.io.File

enum class ThumbnailFailure { SourceMissing, Unreadable, Unknown }

sealed interface ThumbnailResult {
    data class Success(val file: File) : ThumbnailResult
    data class Failure(val reason: ThumbnailFailure) : ThumbnailResult
}

/** Decodes one frame of a video into [target] (spec §27). Must report problems as a [ThumbnailResult.Failure], never throw. */
interface ThumbnailGenerator {
    suspend fun generate(source: File, positionMs: Long, widthPx: Int, target: File): ThumbnailResult
}

/** Where finished thumbnails live; the file name is derived from the key. */
interface ThumbnailStore {
    fun fileFor(key: String): File
}
