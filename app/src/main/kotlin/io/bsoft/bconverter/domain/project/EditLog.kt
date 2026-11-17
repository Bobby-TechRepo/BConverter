package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack

/** Everything undoable that is not the timeline: the subtitle tracks, the text overlays and the canvas. Immutable, so a snapshot is cheap. */
data class ContentSnapshot(
    val subtitleTracks: List<SubtitleTrack>, val textOverlays: List<TextOverlay>,
    val canvas: io.bsoft.bconverter.domain.canvas.CanvasSettings = io.bsoft.bconverter.domain.canvas.CanvasSettings(),
) {
    companion object { val Empty = ContentSnapshot(emptyList(), emptyList()) }
}

/** What one undo or redo has to do. */
sealed interface EditStep {
    /** The timeline's own history takes this step. */
    data object Timeline : EditStep
    /** Put [snapshot] back as the subtitles and overlays. */
    data class Content(val snapshot: ContentSnapshot) : EditStep
}

/**
 * One undo/redo order across the timeline and the subtitles/text overlays, so a single Undo button steps back through edits in
 * the order they were made. The timeline keeps its own commands; this log only remembers which kind of edit came when, and
 * the content as it was before each content edit. A new edit clears everything that could be redone. At most [limit] steps.
 */
class EditLog(private val limit: Int = 100) {
    private enum class Kind { TIMELINE, CONTENT }

    private val undoKinds = ArrayDeque<Kind>()
    private val redoKinds = ArrayDeque<Kind>()
    private val undoContent = ArrayDeque<ContentSnapshot>()
    private val redoContent = ArrayDeque<ContentSnapshot>()

    val canUndo: Boolean get() = undoKinds.isNotEmpty()
    val canRedo: Boolean get() = redoKinds.isNotEmpty()

    /** A timeline edit was made. */
    fun recordTimeline() = push(Kind.TIMELINE)

    /** A content edit was made; [before] is the content just before it. */
    fun recordContent(before: ContentSnapshot) { undoContent.addLast(before); push(Kind.CONTENT) }

    private fun push(kind: Kind) {
        undoKinds.addLast(kind); redoKinds.clear(); redoContent.clear()
        while (undoKinds.size > limit) if (undoKinds.removeFirst() == Kind.CONTENT) undoContent.removeFirst()
    }

    /** The step to undo, given the content as it is now; null if there is nothing to undo. */
    fun undo(current: ContentSnapshot): EditStep? {
        val kind = undoKinds.removeLastOrNull() ?: return null
        redoKinds.addLast(kind)
        if (kind == Kind.TIMELINE) return EditStep.Timeline
        redoContent.addLast(current)
        return EditStep.Content(undoContent.removeLast())
    }

    /** The step to redo, given the content as it is now; null if there is nothing to redo. */
    fun redo(current: ContentSnapshot): EditStep? {
        val kind = redoKinds.removeLastOrNull() ?: return null
        undoKinds.addLast(kind)
        if (kind == Kind.TIMELINE) return EditStep.Timeline
        undoContent.addLast(current)
        return EditStep.Content(redoContent.removeLast())
    }

    fun clear() { undoKinds.clear(); redoKinds.clear(); undoContent.clear(); redoContent.clear() }
}
