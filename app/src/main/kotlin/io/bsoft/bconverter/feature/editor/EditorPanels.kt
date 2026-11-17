package io.bsoft.bconverter.feature.editor

/**
 * The tool panels that open inside the editor (implementation plan step 23, part 2). A panel sits above the tools bar on
 * phones and beside the preview on wide windows, so the picture stays in view while a setting is changed. Rename and
 * Export stay dialogs: one names the project, the other ends the session's work.
 */
enum class EditorPanel { NONE, TRIM, SPEED, TRANSITION, VOLUME, SOUND, FILTER, CROP, FREEZE, PIP, CANVAS, TEXT, SUBTITLES, CUE }

/** Which panel is showing — decided here, not inside Composables, so it can be tested on the JVM. */
object EditorPanels {
    /**
     * The panel to show when [requested] was opened last. A subtitle cue picked on the timeline is edited on top of whatever
     * was open (closing it brings that back); picking several clips shows no panel; a panel whose tool no longer applies to
     * what is selected (the clip was deleted, another kind of clip was picked) closes.
     */
    fun shown(requested: EditorPanel, state: EditorUiState, tools: EditorToolState): EditorPanel {
        if (state.multiSelection != null) return EditorPanel.NONE
        if (hasCue(state)) return EditorPanel.CUE
        val applies = when (requested) {
            EditorPanel.NONE, EditorPanel.CUE -> false
            EditorPanel.TRIM -> tools.trim
            EditorPanel.SPEED -> tools.speed
            EditorPanel.TRANSITION -> tools.transition
            EditorPanel.VOLUME -> tools.volume
            EditorPanel.SOUND -> tools.sound
            EditorPanel.FILTER -> tools.filter
            EditorPanel.CROP -> tools.transform
            EditorPanel.FREEZE -> tools.freezeLength
            EditorPanel.PIP -> tools.pipLayout
            // The shape of the output belongs to the project, not to a clip; it stays open whatever is selected.
            EditorPanel.CANVAS -> state.editor.timeline.videoClips.isNotEmpty()
            EditorPanel.TEXT -> state.selectedOverlayId != null && state.textOverlays.any { it.id == state.selectedOverlayId }
            EditorPanel.SUBTITLES -> true
        }
        return if (applies) requested else EditorPanel.NONE
    }

    /** A tool's button was tapped while [current] was showing: the same tool closes its panel, any other opens in its place. */
    fun toggled(current: EditorPanel, tapped: EditorPanel): EditorPanel = if (current == tapped) EditorPanel.NONE else tapped

    private fun hasCue(state: EditorUiState): Boolean {
        val ref = state.selectedCue ?: return false
        return state.subtitleTracks.firstOrNull { it.id == ref.trackId }?.cues?.any { it.id == ref.cueId } == true
    }
}
