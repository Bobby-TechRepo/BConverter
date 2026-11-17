package io.bsoft.bconverter.domain.share

/** What BConverter can do with videos another app shared with it (implementation plan step 25). */
enum class ShareAction {
    /** Start a new editor project with the videos on the timeline, in the order shared. */
    EDIT,
    /** One video: the converter, with every setting available. */
    CONVERT,
    /** Several videos: one quality and format for all, into one folder. */
    CONVERT_ALL,
    EXTRACT_AUDIO,
    EXTRACT_SUBTITLES,
}

/** Reading a share ("Send" / "Send multiple") without Android types, so it can be tested on the JVM. */
object ShareIntents {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    /** The most files taken from one share; the rest are left out (the Photo Picker has the same limit). */
    const val MAX_ITEMS = 50

    /**
     * The shared items, in order, without repeats or blanks. [single] is a "Send" stream, [multiple] the streams of
     * "Send multiple", [clip] the share's clip data (some apps fill only that). Anything that is not a share gives an
     * empty list. Whether an item really is a video is found out when it is opened.
     */
    fun <T : Any> items(action: String?, single: T?, multiple: List<T?>?, clip: List<T?>?): List<T> {
        val raw: List<T?> = when (action) {
            ACTION_SEND -> listOf(single).takeIf { single != null } ?: clip.orEmpty()
            ACTION_SEND_MULTIPLE -> multiple?.takeIf { it.any { item -> item != null } } ?: clip.orEmpty()
            else -> emptyList()
        }
        return raw.filterNotNull().distinct().take(MAX_ITEMS)
    }

    /** What is offered for [count] shared videos: the single-file tools only for one, batch conversion only for several. */
    fun actionsFor(count: Int): List<ShareAction> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf(ShareAction.EDIT, ShareAction.CONVERT, ShareAction.EXTRACT_AUDIO, ShareAction.EXTRACT_SUBTITLES)
        else -> listOf(ShareAction.EDIT, ShareAction.CONVERT_ALL)
    }
}
