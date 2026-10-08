package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ContentInfo
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.ValidationResult

class MediaFileValidator {
    fun validate(info: ContentInfo): ValidationResult {
        if (info.sizeBytes == 0L) return ValidationResult.Invalid(InputError.Empty)
        val mime = info.mimeType?.lowercase()
        val extension = info.displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val mimeIsMedia = mime != null &&
            (mime.startsWith("video/") || mime.startsWith("audio/") || mime in EXTRA_MEDIA_MIME_TYPES)
        val mimeIsGeneric = mime == null || mime == "application/octet-stream"
        return if (mimeIsMedia || (mimeIsGeneric && extension in MEDIA_EXTENSIONS)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(InputError.UnsupportedType(info.mimeType))
        }
    }

    private companion object {
        val EXTRA_MEDIA_MIME_TYPES = setOf("application/x-matroska", "application/ogg")
        val MEDIA_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "3gp", "webm", "mov", "avi", "ts", "mts", "mpg", "mpeg",
            "mp3", "aac", "m4a", "flac", "wav", "opus", "ogg", "ac3",
        )
    }
}
