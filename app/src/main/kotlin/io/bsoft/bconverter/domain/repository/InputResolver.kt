package io.bsoft.bconverter.domain.repository

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput

/** Turns any [MediaSource] into a readable [java.io.File], staging content:// URIs when needed. */
interface InputResolver {
    suspend fun resolve(source: MediaSource): ResolveResult

    /** Deletes the input if it is a staged copy; never touches the user's own files. */
    fun release(input: ResolvedInput)
}
