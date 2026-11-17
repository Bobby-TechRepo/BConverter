package io.bsoft.bconverter.domain.overlay

/** A picture file in the app's overlay-image folder. */
data class StoredImage(val name: String, val modifiedMs: Long)

/**
 * Finds imported overlay pictures no saved project refers to any more (a removed overlay, a deleted project). A picture stays if its
 * name appears in any saved project, or if it is newer than [GRACE_MS]: it may belong to an editor that has not saved yet, and undo
 * can bring a removed picture back until then.
 */
object OverlayImageCleanup {
    const val GRACE_MS = 24L * 60 * 60 * 1000
    private val OURS = Regex("img-[0-9a-fA-F-]+\\.png")

    fun unused(files: List<StoredImage>, savedProjects: List<String>, nowMs: Long): List<String> =
        files.filter { OURS.matches(it.name) && nowMs - it.modifiedMs >= GRACE_MS && savedProjects.none { json -> json.contains(it.name) } }.map { it.name }
}
