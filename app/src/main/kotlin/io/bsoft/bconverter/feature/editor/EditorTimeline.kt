package io.bsoft.bconverter.feature.editor

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import io.bsoft.bconverter.domain.timeline.SeekRange
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import io.bsoft.bconverter.domain.timeline.EdgeTrim
import io.bsoft.bconverter.domain.timeline.Snapping
import io.bsoft.bconverter.domain.timeline.TrimEdge
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import io.bsoft.bconverter.domain.timeline.TransitionType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import io.bsoft.bconverter.domain.timeline.PlayheadNavigator
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import io.bsoft.bconverter.R
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.domain.media.Waveform
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.ThumbnailPlanner
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How far the accessibility step actions move the playhead. */
private const val STEP_MS = 1_000L
/** Step 24: how close (dp) a dragged edge must come to another edge to stick to it. */
private const val SNAP_DP = 12f
private const val HANDLE_DP = 18f
private const val MIN_BLOCK_DP = 8f
private val RulerHeight = 24.dp
private val LaneHeight = 48.dp
private val LaneGap = 4.dp
private val LabelWidth = 56.dp
private val TrailingSpace = 48.dp
private const val ThumbnailTileDp = 80f
private const val WaveformBarDp = 3f
private val VideoAudioStripDp = 14.dp

/** Horizontal space around the clips (lane labels + trailing space), in dp; subtract it from the viewport when fitting. */
internal val TimelineChromeDp: Float = LabelWidth.value + TrailingSpace.value

/**
 * The timeline (spec §21): a ruler, one video lane and the audio lanes, scrolling horizontally together, with a playhead.
 * Positions come from [TimelineScale] (in dp); this composable only draws and reports touches. Zoom also has buttons
 * elsewhere, so the pinch gesture is a convenience rather than the only way.
 */
@Composable
fun TimelineView(
    timeline: Timeline,
    selectedClipId: String?,
    playheadMs: Long,
    scale: TimelineScale,
    missingClipIds: Set<String>,
    scrollState: TimelineScrollState,
    onSelect: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Thumbnails and waveforms; null draws plain blocks. */
    media: TimelineMedia? = null,
    /** The timeline whose sources are readable files (the preview timeline); clips are matched by id. */
    mediaTimeline: Timeline = timeline,
    /** Playing: the timeline follows the playhead; touching it to scroll first calls [onScrub] so playback pauses. */
    isPlaying: Boolean = false,
    onScrub: () -> Unit = {},
    /** Long-press and drag: a video clip dropped at a new place in the order. */
    onMoveVideo: (clipId: String, toIndex: Int) -> Unit = { _, _ -> },
    /** Long-press and drag: an audio clip dropped at a new start time, on a lane. */
    onMoveAudio: (clipId: String, startMs: Long, laneIndex: Int) -> Unit = { _, _, _ -> },
    /** Tapping the marker on a cut: edit the transition before [clipId]. */
    onTransitionClick: (clipId: String) -> Unit = {},
    /** Subtitle tracks, one lane each below the audio lanes; tapping a cue edits it, long-press and drag moves it in time. */
    subtitleTracks: List<io.bsoft.bconverter.domain.subtitle.SubtitleTrack> = emptyList(),
    selectedCue: CueRef? = null,
    onCueClick: (trackId: String, cueId: String) -> Unit = { _, _ -> },
    onMoveCue: (trackId: String, cueId: String, deltaMs: Long) -> Unit = { _, _, _ -> },
    /** Text overlays, packed into rows below the subtitle lanes; tapping one selects it, long-press and drag moves it in time. */
    textOverlays: List<io.bsoft.bconverter.domain.overlay.TextOverlay> = emptyList(),
    selectedOverlayId: String? = null,
    onOverlayClick: (overlayId: String) -> Unit = {},
    onMoveOverlay: (overlayId: String, deltaMs: Long) -> Unit = { _, _ -> },
    /** Long-press and drag: an overlay video dropped at a new start time. */
    onMovePip: (clipId: String, startMs: Long) -> Unit = { _, _ -> },
    /** Step 24: clips picked while selecting several (null: not selecting); they are highlighted and show no handles. */
    multiSelection: Set<String>? = null,
    /** Step 24: an edge handle of the selected clip was dragged by a (clamped, snapped) timeline distance. */
    onTrimEdge: (clipId: String, edge: TrimEdge, deltaMs: Long) -> Unit = { _, _, _ -> },
) {
    val density = LocalDensity.current
    val currentOnZoom by rememberUpdatedState(onZoom)
    var viewportPx by remember { mutableFloatStateOf(0f) }
    // The slice of the content worth decorating: the viewport plus a margin, moving in coarse steps so scrolling does not recompose per pixel.
    // The playhead is fixed in the middle of the view and the timeline moves under it: half a viewport of space before the
    // first clip lets time zero reach the middle, and the same after the blank tail lets the end of the seek range reach it.
    val startPadDp = viewportPx / 2f / density.density
    val windowStartDp by remember(startPadDp, density) { derivedStateOf { TimelineWindow.windowStartDp(scrollState.value, density.density, startPadDp) } }
    val viewportDp = viewportPx / density.density
    // Decorations (frames, waveforms) are drawn for a slice just around the viewport…
    val fromDp = windowStartDp - TimelineWindow.STEP_DP
    val toDp = windowStartDp + viewportDp + 2 * TimelineWindow.STEP_DP
    // …and clips are composed for a wider one; nothing outside it exists, and nothing inside is wider than it (step 13).
    val composeFrom = windowStartDp - TimelineWindow.COMPOSE_MARGIN_DP
    val composeTo = windowStartDp + viewportDp + TimelineWindow.COMPOSE_MARGIN_DP
    val startPadPx = startPadDp * density.density
    // Where content x = 0 sits in the viewport right now; read in the layout and draw phases only, so scrolling never recomposes.
    val shift: () -> Float = remember(scrollState, startPadPx) { { startPadPx - scrollState.value } }
    val videoBlocks = remember(timeline, scale) { scale.videoBlocks(timeline) }
    val audioLanes = remember(timeline, scale) { scale.audioLanes(timeline) }
    val joinMarkers = remember(timeline, scale) { scale.joinMarkers(timeline) }
    val subtitleLanes = remember(subtitleTracks, scale) { subtitleTracks.map { scale.subtitleLane(it) } }
    val laneCount = audioLanes.size.coerceAtLeast(1)
    val overlayRows = remember(textOverlays) { io.bsoft.bconverter.domain.overlay.OverlayLanes.assign(textOverlays) }
    val overlayRowCount = (overlayRows.values.maxOrNull() ?: -1) + 1
    val pipRows = remember(timeline.pipClips) { io.bsoft.bconverter.domain.timeline.PipLanes.assign(timeline.pipClips) }
    val pipRowCount = (pipRows.values.maxOrNull() ?: -1) + 1
    val seekLimitMs = SeekRange.limitMs(timeline)
    val contentEndMs = maxOf(seekLimitMs, timeline.pipClips.maxOfOrNull { it.endTimeMs } ?: 0L, SubtitleTimelineExtent.endMs(subtitleTracks), textOverlays.maxOfOrNull { it.endMs } ?: 0L)
    val contentWidthDp = startPadDp + scale.xOf(contentEndMs) + viewportPx / 2f / density.density
    val maxScrollPx = TimelineWindow.maxScrollPx(contentWidthDp, viewportPx, density.density)
    SideEffect { scrollState.updateMax(maxScrollPx) }
    val timelineLabel = stringResource(R.string.editor_timeline)
    val playheadLabel = stringResource(R.string.editor_playhead, formatTime(playheadMs))
    val zoomLabel = stringResource(R.string.editor_zoom, (scale.pxPerSecond / TimelineScale.DEFAULT_PX_PER_SECOND * 100).roundToInt())

    // Everything the gestures do is also available as accessibility actions on the playhead (spec §43).
    val playheadName = stringResource(R.string.editor_playhead_name)
    val currentTimeline by rememberUpdatedState(timeline)
    val currentPlayhead by rememberUpdatedState(playheadMs)
    val currentSelected by rememberUpdatedState(selectedClipId)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentTimelineForScroll by rememberUpdatedState(timeline)
    val currentOnTrimEdge by rememberUpdatedState(onTrimEdge)
    fun isSelected(id: String) = if (multiSelection != null) id in multiSelection else id == selectedClipId
    // Step 24: edges stick to other clips' edges and the playhead when dragged within SNAP_DP of them.
    val snapThresholdMs = (SNAP_DP * 1000f / scale.pxPerSecond).roundToLong()
    fun snapPoints(excludeId: String) = Snapping.points(currentTimeline, currentPlayhead, excludeId)
    /**
     * Handles for the selected clip: a drag (dp) becomes a timeline distance, snaps its edge if close to another edge
     * (not the left edge of a main-track clip, which stays put while the track closes up), and is clamped to what the
     * clip allows. Returns the distance to show (dp) and whether it snapped.
     */
    fun trimHandles(clipId: String, startMs: Long, endMs: Long, snapStart: Boolean): TrimHandles? {
        if (multiSelection != null || clipId != selectedClipId) return null
        if (EdgeTrim.clampDelta(currentTimeline, clipId, TrimEdge.END, -1) == 0L && EdgeTrim.clampDelta(currentTimeline, clipId, TrimEdge.END, 1) == 0L &&
            EdgeTrim.clampDelta(currentTimeline, clipId, TrimEdge.START, -1) == 0L && EdgeTrim.clampDelta(currentTimeline, clipId, TrimEdge.START, 1) == 0L) return null
        return TrimHandles(
            resolve = { edge, dxDp ->
                val raw = (dxDp * 1000f / scale.pxPerSecond).roundToLong()
                val edgeMs = if (edge == TrimEdge.START) startMs else endMs
                val snapTo = if (edge == TrimEdge.START && !snapStart) null else Snapping.target(edgeMs + raw, snapPoints(clipId), snapThresholdMs)
                val wanted = snapTo?.let { it - edgeMs } ?: raw
                val allowed = EdgeTrim.clampDelta(currentTimeline, clipId, edge, wanted)
                TrimResolution(allowed, allowed * scale.pxPerSecond / 1000f, snapTo != null && allowed == wanted)
            },
            onEnd = { edge, deltaMs -> if (deltaMs != 0L) currentOnTrimEdge(clipId, edge, deltaMs) },
        )
    }

    // Scrolling the timeline is seeking: the time under the fixed playhead follows the finger. A step of less than one pixel is
    // ignored, so the timeline following playback (below) does not seek by itself.
    LaunchedEffect(scale) {
        snapshotFlow { scrollState.value }.collect { px ->
            if (!scrollState.isScrollInProgress) return@collect
            val ms = SeekRange.clamp(currentTimelineForScroll, scale.msAt(px / density.density))
            val tolerance = 1.5f * 1000f / (scale.pxPerSecond * density.density)
            if (kotlin.math.abs(ms - currentPlayhead) > tolerance) {
                if (currentIsPlaying) currentOnScrub()
                currentOnSeek(ms)
            }
        }
    }
    // The other direction: whenever the playhead moves for another reason (playback, a tap, a button, zoom) the timeline is
    // scrolled so that time sits under the playhead.
    LaunchedEffect(playheadMs, scale, viewportPx, scrollState.maxValue) {
        if (viewportPx <= 0f || scrollState.isScrollInProgress) return@LaunchedEffect
        val target = (scale.xOf(playheadMs) * density.density).coerceIn(0f, scrollState.maxValue)
        if (kotlin.math.abs(scrollState.value - target) > 1f) scrollState.scrollTo(target)
    }
    val labelBack = stringResource(R.string.editor_action_back_second)
    val labelForward = stringResource(R.string.editor_action_forward_second)
    val labelPreviousEdge = stringResource(R.string.editor_action_previous_edge)
    val labelNextEdge = stringResource(R.string.editor_action_next_edge)
    val labelPreviousClip = stringResource(R.string.editor_action_previous_clip)
    val labelNextClip = stringResource(R.string.editor_action_next_clip)
    val accessibilityActions = remember(labelBack, labelForward, labelPreviousEdge, labelNextEdge, labelPreviousClip, labelNextClip) {
        fun seekTo(ms: Long?) = ms?.let { currentOnSeek(it); true } ?: false
        fun select(direction: Int) = PlayheadNavigator.adjacentClip(currentTimeline, currentSelected, direction)?.let { currentOnSelect(it); true } ?: false
        listOf(
            CustomAccessibilityAction(labelBack) { seekTo(PlayheadNavigator.step(currentPlayhead, -STEP_MS, SeekRange.limitMs(currentTimeline))) },
            CustomAccessibilityAction(labelForward) { seekTo(PlayheadNavigator.step(currentPlayhead, STEP_MS, SeekRange.limitMs(currentTimeline))) },
            CustomAccessibilityAction(labelPreviousEdge) { seekTo(PlayheadNavigator.previousEdge(currentTimeline, currentPlayhead)) },
            CustomAccessibilityAction(labelNextEdge) { seekTo(PlayheadNavigator.nextEdge(currentTimeline, currentPlayhead)) },
            CustomAccessibilityAction(labelPreviousClip) { select(-1) },
            CustomAccessibilityAction(labelNextClip) { select(+1) },
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    val scrollScope = rememberCoroutineScope()
    Row(modifier.clipToBounds().semantics { contentDescription = timelineLabel; stateDescription = zoomLabel }) {
        // Fixed lane labels.
        Column(Modifier.width(LabelWidth)) {
            Box(Modifier.height(RulerHeight))
            LaneLabel(stringResource(R.string.editor_track_video))
            repeat(pipRowCount) { LaneLabel(stringResource(R.string.editor_track_pip)) }
            repeat(laneCount) { LaneLabel(stringResource(R.string.editor_track_audio)) }
            subtitleTracks.forEach { LaneLabel(it.name) }
            repeat(overlayRowCount) { LaneLabel(stringResource(R.string.editor_track_text)) }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .onSizeChanged { viewportPx = it.width.toFloat() }
                .pinchToZoom(currentOnZoom)
                .scrollable(scrollState, Orientation.Horizontal, reverseDirection = ScrollableDefaults.reverseDirection(layoutDirection, Orientation.Horizontal, false))
                // The same scroll semantics horizontalScroll gives, so TalkBack and UI tests can still scroll the timeline.
                .semantics {
                    horizontalScrollAxisRange = ScrollAxisRange(value = { scrollState.value }, maxValue = { scrollState.maxValue })
                    scrollBy { x, _ -> scrollScope.launch { scrollState.animateScrollBy(x) }; true }
                },
        ) {
            Box(
                Modifier.fillMaxSize().pointerInput(scale, startPadDp) {
                    detectTapGestures { offset -> onSeek(SeekRange.clamp(currentTimeline, scale.msAt((offset.x + scrollState.value) / density.density - startPadDp))) }
                },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Ruler(scale, seekLimitMs, shift)
                    TimelineLane(Modifier.fillMaxWidth().height(LaneHeight)) {
                        videoBlocks.forEachIndexed { i, block ->
                            val shown = TimelineWindow.clip(block, composeFrom, composeTo)
                            if (shown != null) key(block.id) {
                                val clip = timeline.videoClips[i]
                                ClipBlockView(
                                    shown.block, selected = isSelected(block.id), missing = block.id in missingClipIds,
                                    label = videoLabel(clip), description = stringResource(R.string.editor_clip_video, i + 1, videoBlocks.size, formatTime(clip.durationMs)),
                                    trim = trimHandles(clip.id, clip.startTimeMs, clip.endTimeMs, snapStart = false),
                                    isVideo = true, onClick = { onSelect(block.id) },
                                    onDragStart = { onSelect(block.id) },
                                    onDragEnd = { dxDp, _ ->
                                        val deltaMs = (dxDp * 1000f / scale.pxPerSecond).roundToLong()
                                        onMoveVideo(clip.id, ClipDrag.videoIndex(currentTimeline, clip.id, deltaMs))
                                    },
                                    decorated = media != null,
                                    shiftPx = shift,
                                    decor = {
                                        val file = mediaTimeline.fileOf(clip.id)
                                        VideoThumbnails(clip, file, block, media, fromDp, toDp, shown.cutDp)
                                        ClipWaveform(clip.sourceStartMs, clip.sourceEndMs, file, block, media, fromDp, toDp, onPicture = true, cutDp = shown.cutDp)
                                    },
                                )
                            }
                        }
                        joinMarkers.forEach { marker ->
                            if (marker.centerPx in composeFrom..composeTo) key(marker.clipId) {
                                TransitionMarker(marker, shift, onClick = { onTransitionClick(marker.clipId) })
                            }
                        }
                    }
                    repeat(pipRowCount) { row ->
                        TimelineLane(Modifier.padding(top = LaneGap).fillMaxWidth().height(LaneHeight)) {
                            timeline.pipClips.filter { pipRows[it.id] == row }.forEach { clip ->
                                val full = scale.pipBlock(clip)
                                val shown = TimelineWindow.clip(full, composeFrom, composeTo)
                                if (shown != null) key(clip.id) {
                                    val number = timeline.pipClips.indexOf(clip) + 1
                                    ClipBlockView(
                                        shown.block, selected = isSelected(clip.id), missing = clip.id in missingClipIds,
                                        label = formatTime(clip.durationMs), description = stringResource(R.string.editor_clip_pip, number, formatTime(clip.durationMs)),
                                        trim = trimHandles(clip.id, clip.startTimeMs, clip.endTimeMs, snapStart = true),
                                        isVideo = true, onClick = { onSelect(clip.id) },
                                        keyframeXs = scale.keyframeXs(full, clip.keyframes.map { it.offsetMs }, clip.durationMs).map { it - shown.cutDp },
                                        onDragStart = { onSelect(clip.id) },
                                        onDragEnd = { dxDp, _ ->
                                            val start = (clip.startTimeMs + dxDp * 1000f / scale.pxPerSecond).roundToLong()
                                            onMovePip(clip.id, Snapping.snapMove(start, clip.durationMs, snapPoints(clip.id), snapThresholdMs).coerceAtLeast(0L))
                                        },
                                        shiftPx = shift,
                                    )
                                }
                            }
                        }
                    }
                    repeat(laneCount) { lane ->
                        TimelineLane(Modifier.padding(top = LaneGap).fillMaxWidth().height(LaneHeight)) {
                            audioLanes.getOrNull(lane)?.forEachIndexed { i, block ->
                                val shown = TimelineWindow.clip(block, composeFrom, composeTo)
                                if (shown != null) key(block.id) {
                                    val clip = timeline.audioTracks[lane].clips.sortedBy { it.startTimeMs }[i]
                                    ClipBlockView(
                                        shown.block, selected = isSelected(block.id), missing = block.id in missingClipIds,
                                        label = audioLabel(clip), description = stringResource(R.string.editor_clip_audio, i + 1, formatTime(clip.durationMs)),
                                        trim = trimHandles(clip.id, clip.startTimeMs, clip.endTimeMs, snapStart = true),
                                        isVideo = false, onClick = { onSelect(block.id) },
                                        onDragStart = { onSelect(block.id) },
                                        onDragEnd = { dxDp, dyPx ->
                                            val deltaMs = (dxDp * 1000f / scale.pxPerSecond).roundToLong()
                                            val step = with(density) { (LaneHeight + LaneGap).toPx() }
                                            val start = Snapping.snapMove(ClipDrag.audioStart(clip.startTimeMs, deltaMs), clip.durationMs, snapPoints(clip.id), snapThresholdMs).coerceAtLeast(0L)
                                            onMoveAudio(clip.id, start, ClipDrag.audioLane(lane, laneCount, dyPx, step))
                                        },
                                        shiftPx = shift,
                                        decor = { ClipWaveform(clip.sourceStartMs, clip.sourceEndMs, mediaTimeline.fileOf(clip.id), block, media, fromDp, toDp, onPicture = false, cutDp = shown.cutDp) },
                                    )
                                }
                            }
                        }
                    }
                    subtitleTracks.forEachIndexed { lane, track ->
                        TimelineLane(Modifier.padding(top = LaneGap).fillMaxWidth().height(LaneHeight)) {
                            val cues = track.effectiveCues()
                            subtitleLanes[lane].forEachIndexed { i, block ->
                                val shown = TimelineWindow.clip(block, composeFrom, composeTo)
                                if (shown != null) key(block.id) {
                                    val cue = cues[i]
                                    ClipBlockView(
                                        shown.block, selected = selectedCue?.cueId == cue.id, missing = false,
                                        label = cue.text.replace("\n", " "),
                                        description = stringResource(R.string.editor_subtitle_cue, track.name, i + 1, formatTime(cue.durationMs), cue.text.replace("\n", " ")),
                                        isVideo = false, subtitle = true, onClick = { onCueClick(track.id, cue.id) },
                                        onDragEnd = { dxDp, _ -> onMoveCue(track.id, cue.id, (dxDp * 1000f / scale.pxPerSecond).roundToLong()) },
                                        shiftPx = shift,
                                    )
                                }
                            }
                        }
                    }
                    repeat(overlayRowCount) { row ->
                        TimelineLane(Modifier.padding(top = LaneGap).fillMaxWidth().height(LaneHeight)) {
                            textOverlays.filter { overlayRows[it.id] == row }.forEach { o ->
                                val full = scale.overlayBlock(o)
                                val shown = TimelineWindow.clip(full, composeFrom, composeTo)
                                if (shown != null) key(o.id) {
                                    ClipBlockView(
                                        shown.block, selected = selectedOverlayId == o.id, missing = false,
                                        label = if (o.isImage) (o.name ?: stringResource(R.string.image_overlay_name)) else o.text.replace("\n", " ").ifBlank { stringResource(R.string.editor_track_text) },
                                        description = if (o.isImage) stringResource(R.string.editor_image_overlay, o.name ?: stringResource(R.string.image_overlay_name), formatTime(o.startMs), formatTime(o.durationMs)) else stringResource(R.string.editor_text_overlay, o.text.replace("\n", " "), formatTime(o.startMs), formatTime(o.durationMs)),
                                        isVideo = false, overlay = true, onClick = { onOverlayClick(o.id) },
                                        keyframeXs = scale.keyframeXs(full, o.keyframes.map { it.offsetMs }, o.durationMs).map { it - shown.cutDp },
                                        onDragEnd = { dxDp, _ -> onMoveOverlay(o.id, (dxDp * 1000f / scale.pxPerSecond).roundToLong()) },
                                        shiftPx = shift,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Playhead: fixed in the middle of the view; the timeline scrolls underneath it.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(BConverterTheme.brand.playhead)
                        .semantics {
                            contentDescription = playheadName
                            stateDescription = playheadLabel
                            if (seekLimitMs > 0) {
                                progressBarRangeInfo = ProgressBarRangeInfo(playheadMs.toFloat(), 0f..seekLimitMs.toFloat())
                                setProgress { onSeek(it.roundToLong().coerceIn(0L, seekLimitMs)); true }
                            }
                            customActions = accessibilityActions
                        },
                )
                // A small cap on the playhead so it reads as a handle, not a hairline.
                Box(
                    Modifier.align(Alignment.TopCenter).width(14.dp).height(10.dp)
                        .clip(RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp))
                        .background(BConverterTheme.brand.playhead),
                )
        }
    }
}

/**
 * One timeline lane, as wide as the viewport. Unlike a Box it measures its children with no width limit: a clip runs past
 * the screen edge at its real length (up to the composed window, see [TimelineWindow]) instead of being squeezed to the
 * lane's width. Children place themselves horizontally (from the scroll offset), so everything is placed at x = 0 here.
 */
@Composable
private fun TimelineLane(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val childConstraints = Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(childConstraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.placeRelative(0, (constraints.maxHeight - it.height) / 2) }
        }
    }
}

/** The button on a cut between two video clips: small for a plain cut, as wide as the transition otherwise. */
@Composable
private fun TransitionMarker(marker: JoinMarker, shiftPx: () -> Float, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val width by androidx.compose.animation.core.animateDpAsState(marker.widthPx.dp, label = "transition marker width")
    val center by androidx.compose.animation.core.animateDpAsState(marker.centerPx.dp, label = "transition marker position")
    val active = marker.effect.isActive
    val typeLabel = stringResource(
        when (marker.effect.type) {
            TransitionType.NONE -> R.string.editor_transition_none
            TransitionType.FADE_BLACK -> R.string.editor_transition_black
            TransitionType.CROSSFADE -> R.string.editor_transition_cross
        },
    )
    val description = stringResource(
        R.string.editor_transition_marker,
        marker.clipIndex + 1,
        if (active) typeLabel + ", " + stringResource(R.string.editor_transition_seconds, formatSpeed(marker.effect.durationMs / 1000f)) else typeLabel,
    )
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .offset { IntOffset(((center - width / 2).toPx() + shiftPx()).roundToInt(), 0) }
            .width(width)
            // The tap area is the minimum 48 dp tall; the pill drawn inside it stays 32 dp.
            .height(48.dp)
            .zIndex(2f)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(if (active) colors.secondaryContainer else colors.surface.copy(alpha = 0.85f))
            .border(if (active) 2.dp else 1.dp, if (active) colors.secondary else colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_tool_transition), contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LaneLabel(text: String) {
    Box(Modifier.padding(top = LaneGap).height(LaneHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Ruler(scale: TimelineScale, durationMs: Long, shiftPx: () -> Float) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = remember(color) { TextStyle(color = color, fontSize = 10.sp) }
    val ticks = remember(scale, durationMs) { scale.ticks(durationMs) }
    Canvas(Modifier.fillMaxWidth().height(RulerHeight)) {
        val shift = shiftPx()
        val labelRoom = 64.dp.toPx()
        ticks.forEach { t ->
            val x = scale.xOf(t).dp.toPx() + shift
            if (x < -labelRoom || x > size.width) return@forEach
            drawLine(color, Offset(x, size.height - 6.dp.toPx()), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawText(measurer, formatTime(t), Offset(x + 2.dp.toPx(), 0f), style)
        }
    }
}

@Composable
private fun ClipBlockView(
    block: ClipBlock,
    selected: Boolean,
    missing: Boolean,
    label: String,
    description: String,
    isVideo: Boolean,
    onClick: () -> Unit,
    subtitle: Boolean = false,
    overlay: Boolean = false,
    onDragStart: () -> Unit = {},
    /** Called with the horizontal (dp) and vertical (px) distance dragged when a long-press drag ends. */
    onDragEnd: (dxDp: Float, dyPx: Float) -> Unit = { _, _ -> },
    decorated: Boolean = false,
    /** Distances (dp) from the block's left edge where its animation has keyframes; each is drawn as a small diamond. */
    keyframeXs: List<Float> = emptyList(),
    /** Where content x = 0 is in the viewport (px); read while placing, so scrolling moves the block without recomposing it. */
    shiftPx: () -> Float = { 0f },
    /** Step 24: edge handles shown on the selected clip; null for none. */
    trim: TrimHandles? = null,
    decor: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    // Live edge positions while a handle is dragged (dp, already clamped and snapped); reset when the edit lands.
    var startDx by remember(block) { mutableFloatStateOf(0f) }
    var endDx by remember(block) { mutableFloatStateOf(0f) }
    val background = when {
        missing -> colors.errorContainer
        subtitle -> colors.secondaryContainer
        overlay -> colors.surfaceVariant
        isVideo -> colors.primaryContainer
        else -> colors.tertiaryContainer
    }
    val selectedText = stringResource(R.string.editor_clip_selected)
    val missingText = stringResource(R.string.editor_clip_missing)
    val full = buildString {
        append(description)
        if (keyframeXs.isNotEmpty()) append(", ").append(pluralStringResource(R.plurals.editor_clip_keyframes, keyframeXs.size, keyframeXs.size))
        if (missing) append(", ").append(missingText)
        if (selected) append(", ").append(selectedText)
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(((block.xPx + startDx).dp.toPx() + shiftPx() + drag.x).roundToInt(), drag.y.roundToInt()) }
            .alpha(if (dragging) 0.85f else 1f)
            .pointerInput(block.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true; drag = Offset.Zero
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        currentDragStart()
                    },
                    onDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = {
                        val moved = drag
                        dragging = false; drag = Offset.Zero
                        currentDragEnd(moved.x / density.density, moved.y)
                    },
                    onDragCancel = { dragging = false; drag = Offset.Zero },
                )
            }
            .width((block.widthPx - startDx + endDx).coerceAtLeast(MIN_BLOCK_DP).dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.primary else colors.outline.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = full
                this.selected = selected
                role = Role.Button
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        decor()
        keyframeXs.forEach { x ->
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = (x - 4f).dp, y = (-2).dp).size(8.dp)
                    .rotate(45f).background(colors.primary).border(1.dp, colors.surface),
            )
        }
        Text(
            // Never colour alone: missing and selected clips also say so in text.
            text = buildString { if (missing) append("⚠ "); append(label) },
            style = MaterialTheme.typography.labelSmall,
            // The matching "on" colour of the block's background (or of the surface backing drawn over pictures) keeps the label readable.
            color = if (decorated) colors.onSurface else contentColorFor(background),
            maxLines = 1,
            // Over a picture the label needs a backing to stay readable.
            modifier = (if (decorated) Modifier.background(colors.surface.copy(alpha = 0.7f)) else Modifier).padding(horizontal = 6.dp),
        )
        if (trim != null) {
            TrimHandle(TrimEdge.START, trim, Modifier.align(Alignment.CenterStart)) { startDx = it }
            TrimHandle(TrimEdge.END, trim, Modifier.align(Alignment.CenterEnd)) { endDx = it }
        }
    }
}

/** How a dragged edge resolves: the timeline distance to apply, the same in dp to show, and whether it snapped. */
internal data class TrimResolution(val deltaMs: Long, val dxDp: Float, val snapped: Boolean)

/** Step 24: what the edge handles of the selected clip do. */
internal class TrimHandles(
    val resolve: (edge: TrimEdge, rawDxDp: Float) -> TrimResolution,
    val onEnd: (edge: TrimEdge, deltaMs: Long) -> Unit,
)

/**
 * One edge handle: a bar on the clip's edge. Dragging it shows the edge moving (clamped to what the clip allows and
 * stuck to nearby edges, with a tick when it snaps) and applies the trim when the finger lifts.
 */
@Composable
private fun TrimHandle(edge: TrimEdge, handles: TrimHandles, modifier: Modifier, onPreview: (Float) -> Unit) {
    val density = LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val current by rememberUpdatedState(handles)
    val label = stringResource(if (edge == TrimEdge.START) R.string.editor_trim_start_handle else R.string.editor_trim_end_handle)
    Box(
        modifier
            .width(HANDLE_DP.dp)
            .fillMaxHeight()
            .pointerInput(edge) {
                var raw = 0f
                var last: TrimResolution? = null
                detectHorizontalDragGestures(
                    onDragStart = { raw = 0f; last = null },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        raw += amount / density.density
                        val r = current.resolve(edge, raw)
                        if (r.snapped && last?.snapped != true) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        last = r
                        onPreview(r.dxDp)
                    },
                    onDragEnd = {
                        val r = last
                        onPreview(0f)
                        if (r != null) current.onEnd(edge, r.deltaMs)
                    },
                    onDragCancel = { onPreview(0f) },
                )
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxHeight().width(HANDLE_DP.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(if (edge == TrimEdge.START) 8.dp else 0.dp, if (edge == TrimEdge.START) 0.dp else 8.dp, if (edge == TrimEdge.START) 0.dp else 8.dp, if (edge == TrimEdge.START) 8.dp else 0.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onPrimary))
        }
    }
}

@Composable
private fun audioLabel(clip: AudioClip): String =
    if (clip.playbackSpeed == 1f) formatTime(clip.durationMs)
    else formatTime(clip.durationMs) + " · " + stringResource(R.string.editor_clip_speed, formatSpeed(clip.playbackSpeed))

@Composable
private fun videoLabel(clip: VideoClip): String {
    val base = if (clip.playbackSpeed == 1f) formatTime(clip.durationMs)
    else formatTime(clip.durationMs) + " · " + stringResource(R.string.editor_clip_speed, formatSpeed(clip.playbackSpeed))
    return when (clip.transitionIn.type) {
        io.bsoft.bconverter.domain.timeline.TransitionType.NONE -> base
        io.bsoft.bconverter.domain.timeline.TransitionType.FADE_BLACK -> stringResource(R.string.editor_clip_in_black) + " " + base
        io.bsoft.bconverter.domain.timeline.TransitionType.CROSSFADE -> stringResource(R.string.editor_clip_in_cross) + " " + base
    }
}

internal fun formatSpeed(speed: Float): String = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')

internal fun formatTime(ms: Long): String = io.bsoft.bconverter.feature.converter.formatDuration(ms)

private fun Timeline.fileOf(clipId: String): File? = when (val item = findClip(clipId)) {
    is VideoClip -> (item.source as? MediaSource.FileSource)?.file
    is AudioClip -> (item.source as? MediaSource.FileSource)?.file
    else -> null
}

/** Frames of a video clip, only for the tiles near the viewport (spec §27); each loads on its own and a failed one is simply left blank. */
@Composable
private fun BoxScope.VideoThumbnails(clip: VideoClip, file: File?, block: ClipBlock, media: TimelineMedia?, fromDp: Float, toDp: Float, cutDp: Float = 0f) {
    if (file == null || media == null) return
    val tiles = remember(clip.sourceStartMs, clip.sourceEndMs, block.widthPx) { ThumbnailPlanner.plan(clip, block.widthPx, ThumbnailTileDp) }
    val visible = remember(tiles, fromDp, toDp, block.xPx) { ThumbnailPlanner.visible(tiles, fromDp - block.xPx, toDp - block.xPx) }
    val density = LocalDensity.current.density
    // Round the size up to a multiple of 64 px so nearby zoom levels share cached files.
    val widthPx = ((ThumbnailTileDp * density / 64f).toInt() + 1) * 64
    visible.forEach { tile ->
        key(tile.index, tile.positionMs) {
            val frame by produceState<ImageBitmap?>(null, file, tile.positionMs, widthPx) { value = media.frame(file, tile.positionMs, widthPx) }
            frame?.let {
                Image(
                    it, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.offset(x = (tile.xPx - cutDp).dp).width(tile.widthPx.dp).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Loudness of a clip's sound (spec §28), drawn only for the part near the viewport. A video clip's own audio is a strip along
 * its bottom edge over the frames ([onPicture]); an audio clip fills its block.
 */
@Composable
private fun BoxScope.ClipWaveform(
    sourceStartMs: Long, sourceEndMs: Long, file: File?, block: ClipBlock, media: TimelineMedia?, fromDp: Float, toDp: Float, onPicture: Boolean,
    /** How much of the block's start is not laid out (see [VisibleBlock]); the strip is placed relative to what is. */
    cutDp: Float = 0f,
) {
    if (file == null || media == null) return
    val waveform by produceState<Waveform?>(null, file) { value = media.waveform(file) }
    val wave = waveform ?: return
    val startDp = max(fromDp - block.xPx, 0f)
    val endDp = min(toDp - block.xPx, block.widthPx)
    if (endDp <= startDp || block.widthPx <= 0f) return
    val span = sourceEndMs - sourceStartMs
    val fromMs = sourceStartMs + (span * (startDp / block.widthPx)).toLong()
    val toMs = sourceStartMs + (span * (endDp / block.widthPx)).toLong()
    val count = ceil((endDp - startDp) / WaveformBarDp).toInt()
    val bars = remember(wave, count, fromMs, toMs) { wave.bars(count, fromMs, toMs) }
    val color = if (onPicture) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onTertiaryContainer
    val place = if (onPicture) Modifier.align(Alignment.BottomStart).height(VideoAudioStripDp) else Modifier.fillMaxHeight()
    Canvas(place.offset(x = (startDp - cutDp).dp).width((endDp - startDp).dp)) {
        if (onPicture) drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
        val step = WaveformBarDp.dp.toPx()
        bars.forEachIndexed { i, level ->
            val h = max(level * size.height, 2.dp.toPx())
            val x = (i + 0.5f) * step
            drawLine(color, Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), strokeWidth = 2.dp.toPx())
        }
    }
}

/**
 * Two-finger pinch, watched before the children see the touches (Initial pass) and consumed once a pinch is under way, so the
 * horizontal scroller underneath cannot swallow it. The gesture is not keyed on the zoom level: restarting it on every
 * zoom step would drop the pinch after a single frame.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
