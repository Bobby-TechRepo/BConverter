package io.bsoft.bconverter.feature.share

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Videos another app shared with BConverter; [id] tells two shares of the same files apart. */
data class SharedMedia(val id: Long, val uris: List<Uri>)

/**
 * Hands a share from the activity that received it to the screen that uses it (step 25). The activity [deliver]s,
 * the share screen shows what is [pending], and the tool the user picks [take]s it, so it is used exactly once.
 * Kept in memory only: after the process is killed the sending app has to share again.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val _pending = MutableStateFlow<SharedMedia?>(null)
    val pending: StateFlow<SharedMedia?> = _pending.asStateFlow()
    private var nextId = 0L

    /** A new share replaces one that was not used yet; an empty one changes nothing. */
    @Synchronized fun deliver(uris: List<Uri>) {
        if (uris.isNotEmpty()) _pending.value = SharedMedia(++nextId, uris)
    }

    /** The shared files, once: later calls get null until something else is shared. */
    fun take(): List<Uri>? = _pending.getAndUpdate { null }?.uris

    /** The user backed out of the share. */
    fun clear() { _pending.value = null }
}
