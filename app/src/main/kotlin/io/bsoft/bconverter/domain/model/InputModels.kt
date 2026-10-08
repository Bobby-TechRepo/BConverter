package io.bsoft.bconverter.domain.model

import java.io.File

/** Metadata about a selected item; any field may be unknown. */
data class ContentInfo(val displayName: String?, val mimeType: String?, val sizeBytes: Long?)

/** A media input readable as a plain [File] (staged copy or the user's own file). */
data class ResolvedInput(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    /** True when [file] is an app-owned temporary copy that must be released after use. */
    val isStaged: Boolean,
)

sealed interface InputError {
    data object PermissionDenied : InputError
    data object NotFound : InputError
    data object Unreadable : InputError
    data class UnsupportedType(val mimeType: String?) : InputError
    data object Empty : InputError
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : InputError
}

sealed interface ResolveResult {
    data class Success(val input: ResolvedInput) : ResolveResult
    data class Failure(val error: InputError) : ResolveResult
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val error: InputError) : ValidationResult
}
