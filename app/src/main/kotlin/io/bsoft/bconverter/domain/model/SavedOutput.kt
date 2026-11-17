package io.bsoft.bconverter.domain.model

import android.net.Uri

/** A finished result the user chose a place for; kept so it can be shared right away (spec Phase 16). */
data class SavedOutput(val uri: Uri, val mimeType: String)
