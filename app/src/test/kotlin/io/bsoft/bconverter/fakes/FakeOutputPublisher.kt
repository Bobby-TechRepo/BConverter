package io.bsoft.bconverter.fakes

import android.net.Uri
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.repository.PublishResult
import java.io.File

class FakeOutputPublisher : OutputPublisher {
    data class Published(val file: File, val destination: Uri, val content: String?)

    var result: PublishResult = PublishResult.Success
    val published = mutableListOf<Published>()

    override suspend fun publish(source: File, destination: Uri): PublishResult {
        published += Published(source, destination, if (source.exists()) source.readText() else null)
        return result
    }
}
