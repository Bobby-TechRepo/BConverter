package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.core.ui.OrientationPolicy
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.VideoClip

/** Which editor controls are usable right now. */
data class EditorToolState(
    val addVideo: Boolean,
    val addAudio: Boolean,
    val split: Boolean,
    val delete: Boolean,
    val trim: Boolean,
    val speed: Boolean,
    val detach: Boolean,
    val transition: Boolean,
    val volume: Boolean,
    val replace: Boolean,
    val moveEarlier: Boolean,
    val moveLater: Boolean,
    val undo: Boolean,
    val redo: Boolean,
    val export: Boolean,
    /** Colour filters: a video clip is selected. */
    val filter: Boolean = false,
    /** Crop, rotate and flip: a video clip is selected. */
    val transform: Boolean = false,
    /** Reverse: a video clip is selected. */
    val reverse: Boolean = false,
    /** Volume and fades: an audio clip, or a video clip whose own sound is still on it. */
    val sound: Boolean = false,
    /** Freeze frame: the playhead is inside a selected ordinary (not reversed, not frozen) video clip. */
    val freeze: Boolean = false,
    /** Freeze length: a freeze clip is selected. */
    val freezeLength: Boolean = false,
    /** Add a picture-in-picture clip: there is a main video to put it over. */
    val addPip: Boolean = false,
    /** Place, size and mix the selected picture-in-picture clip. */
    val pipLayout: Boolean = false,
    /** Output shape and background: there is a video to shape. */
    val canvas: Boolean = false,
    /** Step 24: delete and close up what came after (a video or audio clip is selected). */
    val rippleDelete: Boolean = false,
    /** Step 24: pick several clips (the timeline has clips). */
    val select: Boolean = false,
)

object EditorTools {
    fun availability(state: EditorUiState): EditorToolState {
        val editor = state.editor
        val timeline = editor.timeline
        val selected = editor.selectedClipId?.let { timeline.findClip(it) }
        val video = selected as? VideoClip
        val audio = selected as? AudioClip
        val pip = editor.selectedClipId?.let { timeline.findPip(it) }
        val videoIndex = video?.let { v -> timeline.videoClips.indexOfFirst { it.id == v.id } } ?: -1
        val playhead = editor.playheadMs
        val frozen = video?.isFreeze == true
        val insideSelected = selected != null && playhead > selected.startTimeMs && playhead < selected.startTimeMs + selected.durationMs
        return EditorToolState(
            addVideo = !state.busy,
            addAudio = !state.busy,
            split = insideSelected && !frozen,
            delete = selected != null || pip != null,
            trim = (selected != null && !frozen) || pip != null,
            speed = selected != null && !frozen,
            detach = video != null && !video.audioMuted && !state.busy,
            transition = videoIndex > 0,
            volume = audio != null,
            replace = selected != null && !state.busy,
            moveEarlier = videoIndex > 0,
            moveLater = videoIndex in 0 until timeline.videoClips.size - 1,
            undo = editor.canUndo,
            redo = editor.canRedo,
            filter = video != null,
            transform = video != null,
            reverse = video != null && !frozen && !state.busy,
            freeze = video != null && !frozen && !video.reversed && insideSelected && !state.busy,
            freezeLength = frozen,
            addPip = timeline.videoClips.isNotEmpty() && !state.busy,
            pipLayout = pip != null,
            canvas = timeline.videoClips.isNotEmpty() && !state.busy,
            rippleDelete = selected != null && !state.busy,
            select = timeline.clipCount > 0 && !state.busy,
            sound = audio != null || (video != null && !video.audioMuted),
            export = timeline.videoClips.isNotEmpty() && !state.busy && state.export !is ExportUiState.Running && state.missingSources.isEmpty(),
        )
    }
}

/**
 * Phones stack preview, timeline and tools; wide windows put a media panel beside the preview (spec §38/§39).
 * Chosen from the window ([forWindow], step 21), so a tablet in narrow split screen gets the phone layout.
 */
enum class EditorLayoutMode {
    PHONE, TABLET;

    companion object {
        fun forWindow(window: io.bsoft.bconverter.core.ui.WindowLayout): EditorLayoutMode = if (window.twoPane) TABLET else PHONE

        fun forSmallestWidthDp(smallestWidthDp: Int): EditorLayoutMode =
            if (OrientationPolicy.isLargeScreen(smallestWidthDp)) TABLET else PHONE
    }
}
