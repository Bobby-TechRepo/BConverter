package io.bsoft.bconverter.fakes

import android.net.Uri
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.repository.PublishResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred

class FakeOutputPublisher : OutputPublisher {
    data class Published(val file: File, val destination: Uri, val content: String?)

    var result: PublishResult = PublishResult.Success
    /** When set, publish() suspends until completed. */
    var gate: CompletableDeferred<Unit>? = null
    val published = mutableListOf<Published>()

    override suspend fun publish(source: File, destination: Uri): PublishResult {
        published += Published(source, destination, if (source.exists()) source.readText() else null)
        gate?.await()
        return result
    }
}
