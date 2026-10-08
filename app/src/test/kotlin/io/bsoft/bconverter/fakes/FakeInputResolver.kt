package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.repository.InputResolver
import java.io.File
import kotlinx.coroutines.CompletableDeferred

class FakeInputResolver : InputResolver {
    var result: ResolveResult = ResolveResult.Success(ResolvedInput(File("/tmp/a.mp4"), "a.mp4", 10, true))
    /** When set, resolve() suspends until completed — lets tests observe the Resolving state. */
    var gate: CompletableDeferred<Unit>? = null
    val resolved = mutableListOf<MediaSource>()
    val released = mutableListOf<ResolvedInput>()

    override suspend fun resolve(source: MediaSource): ResolveResult {
        resolved += source
        gate?.await()
        return result
    }

    override fun release(input: ResolvedInput) { released += input }
}
