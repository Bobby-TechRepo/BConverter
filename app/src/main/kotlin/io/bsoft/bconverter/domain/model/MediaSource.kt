package io.bsoft.bconverter.domain.model

import android.net.Uri
import java.io.File

sealed interface MediaSource {
    /** True when the source cannot be assumed to be a filesystem path (spec §15). */
    val requiresStaging: Boolean

    data class UriSource(val uri: Uri) : MediaSource {
        override val requiresStaging: Boolean = true
    }

    data class FileSource(val file: File) : MediaSource {
        override val requiresStaging: Boolean = false
    }
}
