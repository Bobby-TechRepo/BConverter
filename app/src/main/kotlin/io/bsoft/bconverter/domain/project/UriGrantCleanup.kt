package io.bsoft.bconverter.domain.project

/**
 * Which persisted read grants no saved project needs any more. Android caps persisted grants per app (128 before
 * Android 11, 512 after) and silently drops the oldest beyond that, which would turn old projects' media "missing";
 * releasing the ones nothing uses keeps the app well under the cap.
 *
 * A grant is kept when any saved project's serialized text mentions its URI, plainly or JSON-escaped ("\/" for "/").
 * Pure: [granted] are URI strings as the platform reports them, [savedProjects] the stored project payloads.
 */
object UriGrantCleanup {
    fun unused(granted: List<String>, savedProjects: List<String>): List<String> =
        granted.filter { uri ->
            val escaped = uri.replace("/", "\\/")
            savedProjects.none { payload -> payload.contains(uri) || payload.contains(escaped) }
        }
}
