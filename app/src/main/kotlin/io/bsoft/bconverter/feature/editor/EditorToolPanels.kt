package io.bsoft.bconverter.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.transform.KenBurnsAvailability

/**
 * True where the editor docks tool panels beside the preview (wide windows): the panel then fills the height it is given and
 * has all its corners rounded. Elsewhere (phones) a panel sits above the tools bar and takes at most [PANEL_HEIGHT_FRACTION]
 * of the screen height.
 */
internal val LocalToolPanelBeside = staticCompositionLocalOf { false }

/** The share of the screen height a panel may take above the tools bar; enough for a few rows, leaving the timeline in view. */
internal const val PANEL_HEIGHT_FRACTION = 0.34f

/**
 * A tool panel inside the editor (implementation plan step 23, part 2). It is part of the editor's own layout, not a window
 * on top of it, so the preview stays visible and can still be dragged, pinched and twisted while numbers are set, and the
 * timeline stays usable. The body scrolls when it is taller than the space given. Back closes the panel ([onDismiss]).
 */
@Composable
internal fun OverlayPanel(title: String, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val beside = LocalToolPanelBeside.current
    val maxHeight = if (beside) Dp.Unspecified else (LocalConfiguration.current.screenHeightDp * PANEL_HEIGHT_FRACTION).dp
    val shape = if (beside) MaterialTheme.shapes.large else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
        // paneTitle: TalkBack announces the panel when it opens or changes, as it would a window.
        Modifier.fillMaxWidth().heightIn(max = maxHeight).semantics { paneTitle = title },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                actions()
            }
            Column(
                Modifier.weight(1f, fill = false).padding(end = 8.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** The plain "Done" of a panel whose changes are already applied. */
@Composable
internal fun PanelDoneButton(onClick: () -> Unit) {
    TextButton(onClick, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.editor_panel_done)) }
}

/**
 * The panel for [panel], showing what is selected now. Each panel is keyed by the selection, so picking another clip, text or
 * cue starts its panel afresh (no half-finished slider is carried over to the next clip). Nothing is drawn when what the
 * panel needs is gone ([EditorPanels.shown] then closes it).
 */
@Composable
internal fun ToolPanelHost(
    panel: EditorPanel,
    state: EditorUiState,
    playheadMs: Long,
    media: TimelineMedia?,
    actions: EditorActions,
    overlayFonts: OverlayFonts,
    onClose: () -> Unit,
) {
    key(panel, state.editor.selectedClipId, state.selectedOverlayId, state.selectedCue) {
        PanelFor(panel, state, playheadMs, media, actions, overlayFonts, onClose)
    }
}

@Composable
private fun PanelFor(
    panel: EditorPanel,
    state: EditorUiState,
    playheadMs: Long,
    media: TimelineMedia?,
    actions: EditorActions,
    overlayFonts: OverlayFonts,
    onClose: () -> Unit,
) {
    val timeline = state.editor.timeline
    val selected = state.editor.selectedClipId?.let { timeline.findClip(it) }
    val pip = state.editor.selectedClipId?.let { timeline.findPip(it) }
    when (panel) {
        EditorPanel.NONE -> Unit
        EditorPanel.TRIM -> when {
            selected is VideoClip -> TrimPanel(selected.sourceDurationMs, selected.sourceStartMs, selected.sourceEndMs, actions.onTrim, onClose)
            selected is AudioClip -> TrimPanel(selected.sourceDurationMs, selected.sourceStartMs, selected.sourceEndMs, actions.onTrim, onClose)
            pip != null -> TrimPanel(pip.sourceDurationMs, pip.sourceStartMs, pip.sourceEndMs, actions.onTrim, onClose)
            else -> Unit
        }
        EditorPanel.SPEED -> when (selected) {
            is VideoClip -> SpeedPanel(selected.playbackSpeed, actions.onSpeed, onClose)
            is AudioClip -> SpeedPanel(selected.playbackSpeed, actions.onSpeed, onClose)
            else -> Unit
        }
        EditorPanel.TRANSITION -> (selected as? VideoClip)?.let { TransitionPanel(it.transitionIn, actions.onTransition, onClose) }
        EditorPanel.VOLUME -> (selected as? AudioClip)?.let { VolumePanel(it.volume, actions.onVolume, onClose) }
        EditorPanel.SOUND -> when (selected) {
            is VideoClip -> ClipSoundPanel(ClipSound(selected.volume, selected.fadeInMs, selected.fadeOutMs), selected.durationMs, actions.onSound, onClose)
            is AudioClip -> ClipSoundPanel(ClipSound(selected.volume, selected.fadeInMs, selected.fadeOutMs), selected.durationMs, actions.onSound, onClose)
            else -> Unit
        }
        EditorPanel.FILTER -> (selected as? VideoClip)?.let { ClipFilterPanel(it.filter, actions.filters, onClose) }
        EditorPanel.CROP -> (selected as? VideoClip)?.let {
            ClipTransformPanel(it.transform, state.selectedSourceSize, actions.transform, onDismiss = onClose, kenBurns = it.kenBurns, showMotion = KenBurnsAvailability.EXPORT_VERIFIED)
        }
        EditorPanel.FREEZE -> (selected as? VideoClip)?.takeIf { it.isFreeze }?.let { FreezeLengthPanel(it.freezeMs, actions.onFreezeLength, onClose) }
        EditorPanel.PIP -> pip?.let { PipPanelWithFrame(it, state, playheadMs, media, actions.pip, onClose) }
        EditorPanel.CANVAS -> CanvasPanel(state.canvas, actions.onCanvas, onClose)
        EditorPanel.TEXT -> state.textOverlays.firstOrNull { it.id == state.selectedOverlayId }?.let {
            if (it.isImage) ImageOverlayPanel(it, actions.overlays, onClose, playheadMs) else TextOverlayDialog(it, overlayFonts, actions.overlays, onClose, playheadMs)
        }
        EditorPanel.SUBTITLES -> SubtitlesPanel(state.subtitleTracks, actions.subtitles, onClose)
        EditorPanel.CUE -> {
            val ref = state.selectedCue
            val track = ref?.let { r -> state.subtitleTracks.firstOrNull { it.id == r.trackId } }
            val cue = ref?.let { r -> track?.cues?.firstOrNull { it.id == r.cueId } }
            if (track != null && cue != null) CuePanel(track, cue, actions.subtitles) { actions.subtitles.onSelectCue(null, null) }
        }
    }
}

/** The overlay panel with a still frame of the clip (at the playhead, or its first frame) for the colour-removal preview. */
@Composable
private fun PipPanelWithFrame(clip: PipClip, state: EditorUiState, playheadMs: Long, media: TimelineMedia?, actions: PipActions, onDismiss: () -> Unit) {
    val file = (state.previewTimeline.findPip(clip.id)?.source as? MediaSource.FileSource)?.file
    val position = clip.sourceStartMs + (playheadMs - clip.startTimeMs).coerceIn(0L, maxOf(clip.durationMs - 1, 0L))
    val frame by produceState<ImageBitmap?>(null, file, position / 250, clip.chromaKey != null) {
        value = if (clip.chromaKey != null && file != null && media != null) media.frame(file, position, 320) else null
    }
    PipPanel(clip, actions, frame, playheadMs - clip.startTimeMs, onDismiss)
}
