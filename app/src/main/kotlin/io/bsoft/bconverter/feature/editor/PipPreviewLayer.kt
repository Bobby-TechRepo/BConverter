package io.bsoft.bconverter.feature.editor

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.ActivePip
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipFade
import io.bsoft.bconverter.domain.timeline.PipKeyframe
import io.bsoft.bconverter.domain.timeline.PipKeyframes
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.domain.timeline.PipPath
import io.bsoft.bconverter.domain.timeline.PipState
import io.bsoft.bconverter.domain.timeline.PipPreview
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The overlay videos that show at [playheadMs], drawn over the preview at their place in the output frame. Each plays in its own
 * player, kept in step with the main video. The selected one can be dragged and pinched; the result is reported once, when the
 * fingers lift ([onLayout]), so a gesture is one undo step. [pips] must use readable (staged) files; others are not shown.
 */
@Composable
fun PipPreviewLayer(
    pips: List<PipClip>,
    selectedId: String?,
    playheadMs: Long,
    isPlaying: Boolean,
    frameAspect: Float,
    onSelect: (String) -> Unit,
    onLayout: (String, PipLayout) -> Unit,
    modifier: Modifier = Modifier,
    onKeyframes: (String, List<PipKeyframe>) -> Unit = { _, _ -> },
) {
    val active = PipPreview.activeAt(pips, playheadMs).filter { (it.clip.source as? MediaSource.FileSource)?.file?.canRead() == true }
    if (active.isEmpty()) return
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
        val aspect = if (frameAspect.isFinite() && frameAspect > 0f) frameAspect else boxW / boxH
        val frameW = if (boxW / boxH > aspect) boxH * aspect else boxW
        val frameH = frameW / aspect
        val density = LocalDensity.current
        Box(Modifier.width(with(density) { frameW.toDp() }).aspectRatio(aspect).clipToBounds()) {
            active.forEach { a ->
                key(a.clip.id) { PipPlayerBox(a, playheadMs, a.clip.id == selectedId, isPlaying, frameW, frameH, onSelect, onLayout, onKeyframes) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PipPlayerBox(
    active: ActivePip,
    timelineMs: Long,
    selected: Boolean,
    isPlaying: Boolean,
    frameW: Float,
    frameH: Float,
    onSelect: (String) -> Unit,
    onLayout: (String, PipLayout) -> Unit,
    onKeyframes: (String, List<PipKeyframe>) -> Unit,
) {
    val clip = active.clip
    val context = LocalContext.current
    val player = remember(clip.id, clip.source, clip.sourceStartMs, clip.sourceEndMs) {
        val file = (clip.source as MediaSource.FileSource).file
        ExoPlayer.Builder(context).build().apply {
            // Sound is the clip's own, never louder than the player allows (volumes above 100% are heard in the export only).
            setMediaItem(
                MediaItem.Builder().setUri(Uri.fromFile(file))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.sourceStartMs).setEndPositionMs(clip.sourceEndMs).build())
                    .build(),
            )
            seekTo((active.sourcePositionMs - clip.sourceStartMs).coerceAtLeast(0))
            prepare()
        }
    }
    var videoAspect by remember(player) { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    player.volume = clip.volume.coerceIn(0f, 1f)
    player.setPlaybackSpeed(clip.speed)
    // Follow the main video: play when it plays, and jump when this player is more than a moment away from where it should be.
    LaunchedEffect(player, isPlaying) { player.playWhenReady = isPlaying }
    LaunchedEffect(player, active.sourcePositionMs, isPlaying) {
        val wanted = (active.sourcePositionMs - clip.sourceStartMs).coerceAtLeast(0)
        if (!isPlaying || abs(player.currentPosition - wanted) > DRIFT_MS) player.seekTo(wanted)
    }

    var draft by remember(clip.layout, clip.keyframes) { mutableStateOf(clip.layout) }
    val currentTimeline by rememberUpdatedState(timelineMs)
    val currentSelected by rememberUpdatedState(selected)
    val currentDraft by rememberUpdatedState(draft)
    val layout = draft
    // A glide that also resizes: the width follows the path (a drag in progress scales the whole of it by what was pinched).
    val look = io.bsoft.bconverter.domain.timeline.PipPath.stateAt(clip, timelineMs)
    val widthPx = (look.width * layout.width / clip.layout.width) * frameW
    val shape = clip.shape
    val sizeChanges = clip.motion?.endWidth != null || clip.keyframes.map { it.width }.distinct().size > 1
    val heightPx = if (shape == io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE) widthPx else widthPx / videoAspect
    // The frame is added around the picture (as in the export), so the whole is a little larger than the picture.
    val borderPx = if (shape == io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE && !sizeChanges) layout.borderWidth * widthPx else 0f
    // Along a glide the centre follows the path; a drag in progress shifts the whole path by what it has moved.
    val centreX = look.x + (layout.centerX - clip.layout.centerX)
    val centreY = look.y + (layout.centerY - clip.layout.centerY)
    val totalW = widthPx + 2 * borderPx
    val totalH = heightPx + 2 * borderPx
    val density = LocalDensity.current
    val description = stringResource(R.string.pip_preview_description)
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset((centreX * frameW - totalW / 2f).roundToInt(), (centreY * frameH - totalH / 2f).roundToInt()) }
            .width(with(density) { totalW.toDp() })
            .aspectRatio(totalW / totalH)
            .alpha(look.opacity * PipFade.factor(clip, timelineMs))
            .clip(when (shape) {
                io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE -> androidx.compose.ui.graphics.RectangleShape
                io.bsoft.bconverter.domain.timeline.PipShape.ROUNDED -> androidx.compose.foundation.shape.RoundedCornerShape((io.bsoft.bconverter.domain.timeline.PipShape.ROUNDED_RADIUS * 100).toInt())
                io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE -> androidx.compose.foundation.shape.CircleShape
            })
            .then(if (selected) Modifier.border(2.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary) else Modifier)
            .then(if (borderPx > 0f) Modifier.background(androidx.compose.ui.graphics.Color(0xFF000000.toInt() or layout.borderRgb)).padding(with(density) { borderPx.toDp() }) else Modifier)
            .semantics { contentDescription = description }
            .pointerInput(clip.id, frameW, frameH) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val wasSelected = currentSelected
                    var moved = false
                    var pan = androidx.compose.ui.geometry.Offset.Zero
                    var zoom = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pan += event.calculatePan(); zoom *= event.calculateZoom()
                        if (wasSelected && (pan.getDistance() > viewConfiguration.touchSlop || abs(zoom - 1f) > 0.03f)) {
                            moved = true
                            draft = PipPreview.transformed(currentDraft, event.calculatePan().x / frameW, event.calculatePan().y / frameH, event.calculateZoom())
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (moved && clip.keyframes.isNotEmpty()) {
                        // A keyframed clip: what was dragged or pinched is the keyframe at the playhead (a new one if there is none).
                        val d = currentDraft
                        val at = PipPath.stateAt(clip, currentTimeline)
                        val look = PipState(
                            PipPreview.snapped(at.x + d.centerX - clip.layout.centerX), PipPreview.snapped(at.y + d.centerY - clip.layout.centerY), at.width * d.width / clip.layout.width, at.opacity,
                        )
                        onKeyframes(clip.id, PipKeyframes.withLookAt(clip, currentTimeline - clip.startTimeMs, look))
                    } else if (moved) onLayout(clip.id, currentDraft.let { it.copy(centerX = PipPreview.snapped(it.centerX), centerY = PipPreview.snapped(it.centerY)) })
                    else onSelect(clip.id)
                }
            },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            update = { it.player = player; it.resizeMode = if (shape == io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val DRIFT_MS = 350L
