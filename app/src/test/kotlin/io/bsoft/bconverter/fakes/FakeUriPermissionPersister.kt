package io.bsoft.bconverter.fakes

import android.net.Uri
import io.bsoft.bconverter.domain.repository.UriPermissionPersister

class FakeUriPermissionPersister : UriPermissionPersister {
    val persisted = mutableListOf<Uri>()
    override fun persist(uri: Uri) { persisted += uri }
}
