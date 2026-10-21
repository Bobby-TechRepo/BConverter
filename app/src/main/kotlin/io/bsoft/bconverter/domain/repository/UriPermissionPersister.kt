package io.bsoft.bconverter.domain.repository

import android.net.Uri

/** Keeps read access to a picked document across app restarts, so a saved project can be reopened. Best effort. */
fun interface UriPermissionPersister {
    fun persist(uri: Uri)
}
