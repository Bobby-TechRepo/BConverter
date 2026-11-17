package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.selected
import io.bsoft.bconverter.domain.timeline.SeekRange
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.ErrorBanner
import io.bsoft.bconverter.core.ui.GradientButton
import io.bsoft.bconverter.core.ui.GradientLinearProgress
import io.bsoft.bconverter.core.ui.IconTile
import io.bsoft.bconverter.core.ui.LightSystemBarIcons
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.QuietButton
import io.bsoft.bconverter.core.ui.StudioTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import io.bsoft.bconverter.core.ui.rememberShareAction
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.feature.export.RequestNotificationPermissionWhen
import io.bsoft.bconverter.feature.preview.PreviewControls
import io.bsoft.bconverter.media.player.Media3TimelinePlayerEngine
import io.bsoft.bconverter.media.player.PlaybackState
import io.bsoft.bconverter.media.player.PlaybackStatus
import io.bsoft.bconverter.media.player.TimelinePlaybackController
import io.bsoft.bconverter.media.player.TimelinePlaybackState
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset

/** Everything the editor UI can ask for; the Route wires these to the ViewModel and the player. */
data class EditorActions(
    val onBack: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onAddVideo: () -> Unit = {},
    val onAddAudio: () -> Unit = {},
    val onReplace: () -> Unit = {},
    val onSplit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTrim: (Long, Long) -> Unit = { _, _ -> },
    val onSpeed: (Float) -> Unit = {},
    val onTransition: (TransitionType, Long) -> Unit = { _, _ -> },
    val onDetachAudio: () -> Unit = {},
    val onReverse: () -> Unit = {},
    val onFreeze: () -> Unit = {},
    val onFreezeLength: (Long) -> Unit = {},
    val onMoveVideo: (String, Int) -> Unit = { _, _ -> },
    val onMoveAudio: (String, Long, Int) -> Unit = { _, _, _ -> },
    val onVolume: (Float) -> Unit = {},
    val onMoveEarlier: () -> Unit = {},
    val onMoveLater: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onExportPreset: (ExportPreset) -> Unit = {},
    val onExportSize: (io.bsoft.bconverter.domain.model.ExportSize) -> Unit = {},
    val onSharePreset: (io.bsoft.bconverter.domain.model.SharePreset) -> Unit = {},
    val onExportContainer: (ContainerFormat) -> Unit = {},
    val onExportStart: () -> Unit = {},
    val onExportCancel: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onShare: (SavedOutput) -> Unit = {},
    val subtitles: SubtitleActions = SubtitleActions(),
    val overlays: TextOverlayActions = TextOverlayActions(),
    val filters: ClipFilterActions = ClipFilterActions(),
    val transform: ClipTransformActions = ClipTransformActions(),
    val onSound: (io.bsoft.bconverter.domain.audio.ClipSound) -> Unit = {},
    val onAddPip: () -> Unit = {},
    val onMovePip: (String, Long) -> Unit = { _, _ -> },
    val pip: PipActions = PipActions(),
    val onCanvas: (io.bsoft.bconverter.domain.canvas.CanvasSettings) -> Unit = {},
    /** Step 24: delete and close up. */
    val onRippleDelete: () -> Unit = {},
    /** Step 24: one edge of a clip dragged on the timeline by a timeline distance. */
    val onTrimEdge: (clipId: String, edge: io.bsoft.bconverter.domain.timeline.TrimEdge, deltaMs: Long) -> Unit = { _, _, _ -> },
    /** Step 24: selecting several clips. */
    val onStartSelect: () -> Unit = {},
    val onToggleSelect: (String) -> Unit = {},
    val onDeleteSelection: (ripple: Boolean) -> Unit = {},
    val onEndSelect: () -> Unit = {},
)

/** The two editor steps that stay dialogs; every tool opens a panel inside the editor instead ([EditorPanel], step 23). */
private enum class EditorDialog { NONE, RENAME, EXPORT }

// ======================= route =======================
@Composable
fun EditorRoute(
    projectId: String?,
    onBack: () -> Unit,
    /** Step 25: videos another app shared with BConverter; they start a new project, in the order shared (added once). */
    sharedVideos: List<android.net.Uri>? = null,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The editor is always dark (studio theme), so the system bars need light icons whatever the app theme is.
    LightSystemBarIcons()
    // Labs (step 20): the composition player, when switched on in Settings; read once, as the editor opens.
    val compositionPreview = hiltViewModel<PreviewEngineViewModel>().compositionPreview
    val engine: io.bsoft.bconverter.media.player.VideoPreviewEngine = remember(compositionPreview) {
        if (compositionPreview) io.bsoft.bconverter.media.player.CompositionTimelinePlayerEngine(context) else Media3TimelinePlayerEngine(context)
    }
    val controller = remember(engine) { TimelinePlaybackController(engine) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause() }
    val playback by controller.state.collectAsStateWithLifecycle()
    val timelineMedia = hiltViewModel<TimelineMediaViewModel>().media
    val share = rememberShareAction()
    val overlayFonts = hiltViewModel<OverlayFontsViewModel>()

    LaunchedEffect(projectId) { if (projectId != null && state.projectId != projectId) viewModel.openProject(projectId) }
    // Default names come from resources so they follow the app language (the ViewModel has no Context).
    val defaultProjectName = stringResource(R.string.editor_default_project_name)
    val defaultOverlayText = stringResource(R.string.editor_default_text)
    LaunchedEffect(defaultProjectName) { if (projectId == null) viewModel.applyDefaultName(defaultProjectName) }
    LaunchedEffect(sharedVideos) { sharedVideos?.let(viewModel::onVideosPicked) }
    // The timeline is the source of truth: whenever it changes, the player is told about it.
    LaunchedEffect(state.previewTimeline) { controller.setTimeline(state.previewTimeline) }
    val isReady = playback.status == PlaybackStatus.Ready || playback.status == PlaybackStatus.Ended
    val playheadMs = if (isReady) playback.positionMs else state.editor.playheadMs
    // Editing works from the ViewModel's playhead, so it is brought up to date whenever playback stops.
    LaunchedEffect(playback.isPlaying) { if (!playback.isPlaying && isReady) viewModel.seekTo(playback.positionMs) }

    // The system Photo Picker (step 22): no storage permission, the gallery's own UI, several clips at once. On devices
    // without it, AndroidX falls back to the document picker by itself.
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_VIDEOS_PER_PICK)) { viewModel.onVideosPicked(it) }
    val pickPip = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onPipPicked(it) }
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onAudioPicked(it) }
    val pickReplacement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onReplacePicked(it) }
    val pickOverlayImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onOverlayImagePicked(it) }
    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onSubtitlePicked(it) }
    val createExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.exportContainer.mimeType)) {
        viewModel.onExportDestinationChosen(it)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.RequestExportDestination -> createExport.launch(event.fileName)
            }
        }
    }
    RequestNotificationPermissionWhen(state.export is ExportUiState.Running)

    // The callbacks are built once per screen, not once per playback frame: anything that changes over time (the playhead,
    // the current state) is read through rememberUpdatedState when a callback runs. A new EditorActions every frame used to
    // make every child that takes it recompose 30 times a second (implementation plan step 14).
    val latestPlayheadMs by rememberUpdatedState(playheadMs)
    val latestState by rememberUpdatedState(state)
    val actions = remember(viewModel, controller, share, context, defaultOverlayText) { EditorActions(
        onBack = onBack,
        onRename = viewModel::renameProject,
        onAddVideo = { pickVideo.launch(videoOnly) },
        onAddPip = { pickPip.launch(videoOnly) },
        onMovePip = viewModel::movePip,
        onCanvas = viewModel::setCanvas,
        pip = PipActions(onLayout = viewModel::setPipLayout, onVolume = viewModel::setPipVolume, onFades = viewModel::setPipFades, onChromaKey = viewModel::setPipChromaKey, onFront = viewModel::bringPipToFront, onMotion = viewModel::setPipMotion, onSpeed = viewModel::setPipSpeed, onShape = viewModel::setPipShape, onKeyframes = viewModel::setPipKeyframes),
        onAddAudio = { pickAudio.launch(arrayOf("audio/*", "video/*")) },
        onReplace = { pickReplacement.launch(arrayOf("video/*", "audio/*")) },
        onSplit = { viewModel.seekTo(latestPlayheadMs); viewModel.splitAtPlayhead() },
        onDelete = viewModel::deleteSelected,
        onRippleDelete = viewModel::rippleDeleteSelected,
        onTrimEdge = viewModel::trimEdge,
        onStartSelect = viewModel::startMultiSelect,
        onToggleSelect = viewModel::toggleInSelection,
        onDeleteSelection = viewModel::deleteMultiSelection,
        onEndSelect = viewModel::endMultiSelect,
        onTrim = viewModel::trimSelected,
        onSpeed = viewModel::setSpeed,
        filters = ClipFilterActions(onSet = viewModel::setFilter, onApplyToAll = viewModel::applyFilterToAll),
        onSound = viewModel::setSound,
        transform = ClipTransformActions(onSet = viewModel::setTransform, onApplyToAll = viewModel::applyTransformToAll, onKenBurns = viewModel::setKenBurns, onKenBurnsToAll = viewModel::applyKenBurnsToAll),
        onTransition = viewModel::setTransition,
        onDetachAudio = viewModel::detachAudioFromSelected,
        onReverse = viewModel::toggleReverse,
        onFreeze = viewModel::freezeFrameAtPlayhead,
        onFreezeLength = viewModel::setFreezeLength,
        onMoveVideo = viewModel::moveVideoClip,
        onMoveAudio = viewModel::moveAudioClip,
        onVolume = viewModel::setVolume,
        onMoveEarlier = {
            val index = latestState.editor.timeline.videoClips.indexOfFirst { it.id == latestState.editor.selectedClipId }
            if (index > 0) viewModel.moveSelectedVideo(index - 1)
        },
        onMoveLater = {
            val index = latestState.editor.timeline.videoClips.indexOfFirst { it.id == latestState.editor.selectedClipId }
            if (index >= 0) viewModel.moveSelectedVideo(index + 1)
        },
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onSelect = viewModel::select,
        onSeek = { ms -> controller.seekTo(ms); viewModel.seekTo(ms) },
        onTogglePlay = controller::togglePlayPause,
        onExportPreset = viewModel::onExportPresetSelected,
        onExportSize = viewModel::onExportSizeSelected,
        onSharePreset = viewModel::applySharePreset,
        onExportContainer = viewModel::onExportContainerSelected,
        onExportStart = viewModel::onExportRequested,
        onExportCancel = viewModel::onCancelExport,
        onDismissMessage = viewModel::dismissMessage,
        onShare = share,
        subtitles = SubtitleActions(
            onImport = { pickSubtitle.launch(arrayOf("*/*")) },
            onAddTrack = { viewModel.addEmptySubtitleTrack { n -> context.getString(R.string.editor_default_subtitle_track, n) } },
            onRemoveTrack = viewModel::removeSubtitleTrack,
            onRenameTrack = viewModel::renameSubtitleTrack,
            onOffset = viewModel::setSubtitleOffset,
            onAddCue = { trackId -> viewModel.addCueAtPlayhead(trackId, latestPlayheadMs) },
            onSelectCue = viewModel::selectCue,
            onEditText = viewModel::editCueText,
            onRetime = viewModel::retimeCue,
            onMoveCue = viewModel::moveCue,
            onDuplicate = viewModel::duplicateCue,
            onSplit = { trackId, cueId -> viewModel.splitCueAtPlayhead(trackId, cueId, latestPlayheadMs) },
            onMerge = viewModel::mergeCueWithNext,
            onDeleteCue = viewModel::deleteCue,
            onExportMode = viewModel::setSubtitleExportMode,
            onLanguage = viewModel::setSubtitleLanguage,
            onDefault = viewModel::setSubtitleDefault,
            onForced = viewModel::setSubtitleForced,
            onStyle = viewModel::setSubtitleStyle,
        ),
        overlays = TextOverlayActions(
            onAdd = { viewModel.addTextOverlay(latestPlayheadMs, defaultOverlayText) },
            onAddImage = { pickOverlayImage.launch(arrayOf("image/*")) },
            onSelect = viewModel::selectOverlay,
            onDelete = viewModel::deleteOverlay,
            onDuplicate = viewModel::duplicateOverlay,
            onText = viewModel::setOverlayText,
            onRetime = viewModel::retimeOverlay,
            onShift = viewModel::shiftOverlay,
            onPosition = viewModel::setOverlayPosition,
            onRotation = viewModel::setOverlayRotation,
            onScale = viewModel::setOverlayScale,
            onWidth = viewModel::setOverlayWidth,
            onStyle = viewModel::updateOverlayStyle,
            onAnimations = viewModel::setOverlayAnimations,
            onKeyframes = viewModel::setOverlayKeyframes,
            onLayer = viewModel::moveOverlayLayer,
            onTransform = viewModel::transformSelectedOverlay,
            onKeyframeTransform = viewModel::transformOverlayKeyframe,
            onGestureEnd = viewModel::endOverlayGesture,
        ),
    ) }
    EditorContent(
        state = state,
        playback = playback,
        playheadMs = playheadMs,
        layout = EditorLayoutMode.forWindow(io.bsoft.bconverter.core.ui.LocalWindowLayout.current),
        actions = actions,
        media = timelineMedia,
        overlayFonts = overlayFonts,
        preview = { modifier ->
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; player = engine.player } },
                update = { it.resizeMode = if (state.canvas.fit == io.bsoft.bconverter.domain.canvas.CanvasFit.FILL) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT },
                onRelease = { it.player = null },
                modifier = modifier,
            )
        },
    )
}

// ======================= content (stateless) =======================
/** The editor UI. Always drawn in the dark studio theme so pictures are judged against a neutral surround. */
@Composable
fun EditorContent(
    state: EditorUiState,
    playback: TimelinePlaybackState,
    playheadMs: Long,
    layout: EditorLayoutMode,
    actions: EditorActions,
    preview: @Composable (Modifier) -> Unit,
    media: TimelineMedia? = null,
    overlayFonts: OverlayFonts = NoOverlayFonts,
) {
    StudioTheme { EditorContentBody(state, playback, playheadMs, layout, actions, preview, media, overlayFonts) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorContentBody(
    state: EditorUiState,
    playback: TimelinePlaybackState,
    playheadMs: Long,
    layout: EditorLayoutMode,
    actions: EditorActions,
    preview: @Composable (Modifier) -> Unit,
    media: TimelineMedia?,
    overlayFonts: OverlayFonts,
) {
    val tools = EditorTools.availability(state.copy(editor = state.editor.copy(playheadMs = playheadMs)))
    var dialog by rememberSaveable { mutableStateOf(EditorDialog.NONE) }
    // The tool panel opened last, and the one showing now (a picked cue is edited on top; a panel whose tool no longer applies closes).
    var panel by rememberSaveable { mutableStateOf(EditorPanel.NONE) }
    val shown = EditorPanels.shown(panel, state, tools)
    LaunchedEffect(shown, panel) { if (shown == EditorPanel.NONE && panel != EditorPanel.NONE) panel = EditorPanel.NONE }
    // A tool's button toggles its panel; opening one puts away a cue being edited.
    val togglePanel: (EditorPanel) -> Unit = { tapped ->
        if (state.selectedCue != null) actions.subtitles.onSelectCue(null, null)
        panel = EditorPanels.toggled(shown, tapped)
    }
    // Tapping a text on the picture or a marker on the timeline opens its panel (never closes it).
    val openPanel: (EditorPanel) -> Unit = { wanted ->
        if (state.selectedCue != null) actions.subtitles.onSelectCue(null, null)
        panel = wanted
    }
    val panelSlot: @Composable (Modifier) -> Unit = { m ->
        ToolPanelSlot(shown, state, playheadMs, media, actions, overlayFonts, m) { panel = EditorPanel.NONE }
    }
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.text()
    val dismissLabel = stringResource(R.string.editor_dismiss)
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText, actionLabel = dismissLabel)
            actions.onDismissMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    TextButton(
                        onClick = { dialog = EditorDialog.RENAME },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text(state.projectName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = { BackButton(actions.onBack) },
                actions = {
                    GradientButton(
                        stringResource(R.string.editor_export), onClick = { dialog = EditorDialog.EXPORT }, enabled = tools.export,
                        minHeight = 40.dp, modifier = Modifier.padding(end = 8.dp), leadingIcon = R.drawable.ic_stat_export,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // The keyboard pushes the editor up (edge to edge, so the window does not resize by itself); the bars' insets are already in [padding].
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val previewArea: @Composable (Modifier) -> Unit = { m -> PreviewArea(media, state, playback, playheadMs, actions, preview, overlayFonts, { openPanel(EditorPanel.TEXT) }, m) }
            val timeline: @Composable (Modifier) -> Unit = { m -> TimelineSection(state, playheadMs, playback.isPlaying, actions, media, m, onTransitionClick = { id -> actions.onSelect(id); openPanel(EditorPanel.TRANSITION) }, onOverlayClick = { id -> actions.overlays.onSelect(id); openPanel(EditorPanel.TEXT) }) }
            when (layout) {
                EditorLayoutMode.PHONE -> {
                    previewArea(Modifier.fillMaxWidth())
                    Banners(state, actions)
                    timeline(Modifier.weight(1f).fillMaxWidth())
                    // Step 23: the open tool's panel, between the timeline and the tools bar; the preview above stays in view.
                    panelSlot(Modifier.fillMaxWidth())
                }
                EditorLayoutMode.TABLET -> {
                    Row(Modifier.weight(1.2f).fillMaxWidth()) {
                        previewArea(Modifier.weight(2f))
                        // On wide windows the panel takes the project panel's place beside the preview, so the timeline keeps its height.
                        Box(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                            if (shown != EditorPanel.NONE) {
                                CompositionLocalProvider(LocalToolPanelBeside provides true) { panelSlot(Modifier.fillMaxSize()) }
                            } else {
                                ProjectPanel(state, tools, actions, Modifier)
                            }
                        }
                    }
                    Banners(state, actions)
                    timeline(Modifier.weight(1f).fillMaxWidth())
                }
            }
            // While typing into a panel the keyboard needs the room; the tools come back when it closes.
            if (!(shown != EditorPanel.NONE && WindowInsets.isImeVisible)) {
                ToolsBar(
                    tools, layout, actions,
                    activePanel = shown, onPanel = togglePanel,
                    hasOverlay = state.selectedOverlayId != null, onAddText = actions.overlays.onAdd, onAddImage = actions.overlays.onAddImage,
                    selectedClipId = state.editor.selectedClipId, multiSelection = state.multiSelection,
                )
            }
        }
    }

    when (dialog) {
        EditorDialog.NONE -> Unit
        EditorDialog.RENAME -> RenameDialog(state.projectName, actions.onRename) { dialog = EditorDialog.NONE }
        EditorDialog.EXPORT -> {
            val subtitlePlan = remember(state.subtitleTracks, state.exportContainer) {
                io.bsoft.bconverter.domain.subtitle.SubtitleExportPlanner.plan(state.subtitleTracks, state.exportContainer)
            }
            val notes = buildList {
                if (subtitlePlan.soft.isNotEmpty()) add(stringResource(R.string.export_note_soft, subtitlePlan.soft.size))
                if (subtitlePlan.burned.isNotEmpty()) add(stringResource(R.string.export_note_burned, subtitlePlan.burned.size))
                if (subtitlePlan.issues.any { it is io.bsoft.bconverter.domain.subtitle.SubtitleExportIssue.StyleSimplified }) add(stringResource(R.string.export_note_style_simplified))
                if (subtitlePlan.blocking.isNotEmpty()) add(stringResource(R.string.msg_export_subtitles_unsupported))
            }
            ExportDialog(
                state.exportPreset, state.exportContainer, state.exportContainers,
                actions.onExportPreset, actions.onExportContainer, actions.onExportStart, { dialog = EditorDialog.NONE },
                notes = notes, canStart = subtitlePlan.blocking.isEmpty(), size = state.exportSize, onSize = actions.onExportSize, onSharePreset = actions.onSharePreset,
                sharePreset = io.bsoft.bconverter.domain.model.SharePreset.matching(state.canvas.aspect, state.exportSize, state.exportPreset),
            )
        }
    }
}

/** Where the open tool panel is drawn; a change of tool fades from one panel to the next and the space grows or shrinks to fit. */
@Composable
private fun ToolPanelSlot(
    shown: EditorPanel,
    state: EditorUiState,
    playheadMs: Long,
    media: TimelineMedia?,
    actions: EditorActions,
    overlayFonts: OverlayFonts,
    modifier: Modifier,
    onClose: () -> Unit,
) {
    AnimatedContent(
        targetState = shown,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) },
        // Above the tools bar a panel grows upwards; beside the preview it hangs from the top.
        contentAlignment = if (LocalToolPanelBeside.current) Alignment.TopCenter else Alignment.BottomCenter,
        label = "tool panel",
    ) { panel ->
        ToolPanelHost(panel, state, playheadMs, media, actions, overlayFonts, onClose)
    }
}

@Composable
private fun PreviewArea(
    media: TimelineMedia?,
    state: EditorUiState,
    playback: TimelinePlaybackState,
    playheadMs: Long,
    actions: EditorActions,
    preview: @Composable (Modifier) -> Unit,
    overlayFonts: OverlayFonts,
    onEditText: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        // Past the last video frame there is nothing to show: a black picture instead of the player.
        val blank = SeekRange.isBlank(state.editor.timeline, playheadMs) && state.editor.timeline.videoClips.isNotEmpty()
        // The stage: a rounded, always-black frame the picture sits in.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large).background(BConverterTheme.brand.stage),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.status == PlaybackStatus.Ready || playback.status == PlaybackStatus.Ended || playback.status == PlaybackStatus.Loading) {
                // The picture sits in a frame of the output's shape, filled with the bar colour (a blurred still sits under the player for a blurred background).
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val shape = if (state.frameAspect.isFinite() && state.frameAspect > 0f) state.frameAspect else 16f / 9f
                    val frame = if (shape > maxWidth / maxHeight) Modifier.fillMaxWidth().aspectRatio(shape) else Modifier.fillMaxHeight().aspectRatio(shape)
                    Box(frame.background(canvasBarColor(state.canvas.background))) {
                        if (state.canvas.background == io.bsoft.bconverter.domain.canvas.CanvasBackground.Blur) BlurredBackdrop(state, playheadMs, media)
                        preview(Modifier.fillMaxSize())
                    }
                }
            }
            if (blank) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
            val dip = io.bsoft.bconverter.domain.timeline.TransitionPreview.blackAlpha(state.editor.timeline, playheadMs)
            if (dip > 0f) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = dip)))
            if (state.previewTimeline.pipClips.isNotEmpty() && !blank) {
                PipPreviewLayer(
                    state.previewTimeline.pipClips.map { p -> state.editor.timeline.findPip(p.id)?.let { p.copy(layout = it.layout, volume = it.volume, keyframes = it.keyframes, motion = it.motion, speed = it.speed, shape = it.shape) } ?: p },
                    state.editor.selectedClipId, playheadMs, playback.isPlaying, state.frameAspect,
                    actions.onSelect, { _, layout -> actions.pip.onLayout(layout) }, Modifier.fillMaxSize(), { _, keys -> actions.pip.onKeyframes(keys) },
                )
            }
            if (state.textOverlays.isNotEmpty()) {
                TextOverlayLayer(
                    state.textOverlays, state.selectedOverlayId, playheadMs, state.frameAspect, state.overlayGuide,
                    overlayFonts, actions.overlays, onEditText, Modifier.fillMaxSize(),
                )
            }
            when (playback.status) {
                PlaybackStatus.Idle -> EmptyStage(stringResource(if (state.editor.timeline.videoClips.isEmpty()) R.string.editor_empty_timeline else R.string.editor_preview_empty))
                PlaybackStatus.Loading -> CircularProgressIndicator()
                else -> Unit
            }
        }
        PreviewControls(
            state = PlaybackState(playback.status, playback.isPlaying, playheadMs.coerceAtMost(playback.durationMs), playback.durationMs, playback.error),
            onPlayPause = actions.onTogglePlay,
            onSeek = actions.onSeek,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** What the stage shows before there is anything to play. */
@Composable
private fun EmptyStage(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconTile(R.drawable.ic_tool_add_video, size = 48.dp, container = Color.White.copy(alpha = 0.08f), tint = Color.White.copy(alpha = 0.85f))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun Banners(state: EditorUiState, actions: EditorActions) {
    if (state.missingSources.isNotEmpty()) {
        ErrorBanner(stringResource(R.string.editor_missing_banner), Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    when (val export = state.export) {
        is ExportUiState.Running -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.editor_exporting, (export.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                GradientLinearProgress(export.progress)
            }
            QuietButton(stringResource(R.string.editor_export_cancel), actions.onExportCancel)
        }
        ExportUiState.Completed -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(BConverterTheme.brand.success))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.editor_export_done), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            state.savedExport?.let { saved -> TextButton({ actions.onShare(saved) }) { Text(stringResource(R.string.share)) } }
            TextButton(actions.onDismissMessage) { Text(stringResource(R.string.editor_dismiss)) }
        }
        ExportUiState.Idle -> Unit
    }
}

@Composable
private fun TimelineSection(state: EditorUiState, playheadMs: Long, isPlaying: Boolean, actions: EditorActions, media: TimelineMedia?, modifier: Modifier, onTransitionClick: (String) -> Unit, onOverlayClick: (String) -> Unit) {
    val timeline = state.editor.timeline
    var scale by remember { mutableStateOf(TimelineScale(TimelineScale.DEFAULT_PX_PER_SECOND)) }
    val scroll = remember { TimelineScrollState() }
    var viewportPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val missingIds = remember(state.missingSources) { state.missingSources.flatMap { it.clipIds }.toSet() }

    Column(modifier) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val zoomOut = stringResource(R.string.editor_zoom_out)
            val zoomIn = stringResource(R.string.editor_zoom_in)
            val fit = stringResource(R.string.editor_zoom_fit)
            ZoomChip("−", zoomOut) { scale = scale.zoomedBy(0.5f) }
            Spacer(Modifier.width(6.dp))
            ZoomChip("+", zoomIn) { scale = scale.zoomedBy(2f) }
            Spacer(Modifier.width(6.dp))
            TextButton(
                {
                    with(density) { scale = TimelineScale.fitting(timeline.durationMs, viewportPx.toDp().value - TimelineChromeDp) }
                },
                Modifier.semantics { contentDescription = fit },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text(fit, style = MaterialTheme.typography.labelMedium) }
        }
        TimelineView(
            timeline = timeline,
            selectedClipId = state.editor.selectedClipId,
            playheadMs = playheadMs,
            scale = scale,
            missingClipIds = missingIds,
            scrollState = scroll,
            // While several clips are being picked, a tap adds or removes a clip instead of selecting it.
            onSelect = if (state.multiSelection != null) actions.onToggleSelect else actions.onSelect,
            multiSelection = state.multiSelection,
            onTrimEdge = actions.onTrimEdge,
            onSeek = actions.onSeek,
            onZoom = { factor -> scale = scale.zoomedBy(factor) },
            modifier = Modifier.fillMaxSize().onSizeChanged { viewportPx = it.width.toFloat() },
            media = media,
            mediaTimeline = state.previewTimeline,
            isPlaying = isPlaying,
            onScrub = actions.onTogglePlay,
            onMoveVideo = actions.onMoveVideo,
            onMoveAudio = actions.onMoveAudio,
            onMovePip = actions.onMovePip,
            onTransitionClick = onTransitionClick,
            subtitleTracks = state.subtitleTracks,
            selectedCue = state.selectedCue,
            onCueClick = actions.subtitles.onSelectCue,
            onMoveCue = actions.subtitles.onMoveCue,
            textOverlays = state.textOverlays,
            selectedOverlayId = state.selectedOverlayId,
            onOverlayClick = onOverlayClick,
            onMoveOverlay = actions.overlays.onShift,
        )
    }
}

@Composable
private fun ProjectPanel(state: EditorUiState, tools: EditorToolState, actions: EditorActions, modifier: Modifier) {
    val timeline = state.editor.timeline
    PremiumCard(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.editor_projects_panel), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.editor_project_summary,
                    pluralStringResource(R.plurals.projects_clip_count, timeline.clipCount, timeline.clipCount),
                    formatTime(timeline.durationMs),
                ),
            )
            GradientButton(stringResource(R.string.editor_add_video), actions.onAddVideo, Modifier.fillMaxWidth(), enabled = tools.addVideo, leadingIcon = R.drawable.ic_tool_add_video)
            QuietButton(stringResource(R.string.editor_add_audio), actions.onAddAudio, Modifier.fillMaxWidth(), enabled = tools.addAudio)
        }
    }
}

@Composable
private fun ToolsBar(
    tools: EditorToolState,
    layout: EditorLayoutMode,
    actions: EditorActions,
    /** The panel showing now: its tool is drawn as on, and tapping it again closes the panel. */
    activePanel: EditorPanel,
    onPanel: (EditorPanel) -> Unit,
    hasOverlay: Boolean,
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    selectedClipId: String? = null,
    multiSelection: Set<String>? = null,
) {
    val label = stringResource(R.string.editor_tools)
    val scroll = rememberScrollState()
    // A different set of tools: start from the left again.
    LaunchedEffect(selectedClipId, multiSelection != null) { scroll.scrollTo(0) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (multiSelection != null) {
                    // Step 24: picking several clips. Only what applies to all of them is offered.
                    ToolGroup(R.string.editor_group_selection, showDivider = false) {
                        Text(
                            stringResource(R.string.editor_selected_count, multiSelection.size),
                            Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ToolButton(R.string.editor_delete, R.drawable.ic_delete, multiSelection.isNotEmpty(), { actions.onDeleteSelection(false) })
                        ToolButton(R.string.editor_ripple_delete, R.drawable.ic_delete, multiSelection.isNotEmpty(), { actions.onDeleteSelection(true) })
                        ToolButton(R.string.editor_select_done, R.drawable.ic_check, true, actions.onEndSelect, accent = true)
                    }
                    return@Row
                }
                // Step 23: only the tools that apply right now are shown (undo and redo always are), so a selected clip
                // brings up its own tools and nothing else; groups with nothing to offer disappear.
                val clip = selectedClipId != null
                if (layout == EditorLayoutMode.PHONE) {
                    ToolGroup(R.string.editor_group_create) {
                        ToolButton(R.string.editor_add_video, R.drawable.ic_tool_add_video, tools.addVideo, actions.onAddVideo, accent = true, hideWhenOff = false)
                        ToolButton(R.string.editor_add_audio, R.drawable.ic_tool_add_audio, tools.addAudio, actions.onAddAudio, hideWhenOff = false)
                    }
                }
                ToolGroup(R.string.editor_group_edit) {
                    ToolButton(R.string.editor_undo, R.drawable.ic_tool_undo, tools.undo, actions.onUndo, hideWhenOff = false)
                    ToolButton(R.string.editor_redo, R.drawable.ic_tool_redo, tools.redo, actions.onRedo, hideWhenOff = false)
                    ToolButton(R.string.editor_split, R.drawable.ic_tool_split, tools.split, actions.onSplit)
                    ToolButton(R.string.editor_trim, R.drawable.ic_tool_trim, tools.trim, { onPanel(EditorPanel.TRIM) }, active = activePanel == EditorPanel.TRIM)
                    ToolButton(R.string.editor_speed, R.drawable.ic_tool_speed, tools.speed, { onPanel(EditorPanel.SPEED) }, active = activePanel == EditorPanel.SPEED)
                    ToolButton(R.string.editor_replace, R.drawable.ic_tool_replace, tools.replace, actions.onReplace)
                    ToolButton(R.string.editor_delete, R.drawable.ic_delete, tools.delete, actions.onDelete)
                    ToolButton(R.string.editor_ripple_delete, R.drawable.ic_delete, tools.rippleDelete, actions.onRippleDelete)
                    ToolButton(R.string.editor_select, R.drawable.ic_check, tools.select, actions.onStartSelect)
                }
                if (tools.sound || tools.volume || tools.detach) {
                    ToolGroup(R.string.editor_group_audio) {
                        ToolButton(R.string.editor_sound, R.drawable.ic_tool_volume, tools.sound, { onPanel(EditorPanel.SOUND) }, active = activePanel == EditorPanel.SOUND)
                        ToolButton(R.string.editor_volume, R.drawable.ic_tool_volume, tools.volume, { onPanel(EditorPanel.VOLUME) }, active = activePanel == EditorPanel.VOLUME)
                        ToolButton(R.string.editor_detach_audio, R.drawable.ic_tool_detach_audio, tools.detach, actions.onDetachAudio)
                    }
                }
                // Adding things is for when no clip is selected; editing the selected text or overlay clip stays reachable.
                if (!clip || hasOverlay || tools.pipLayout) {
                    ToolGroup(R.string.editor_group_text) {
                        if (!clip) {
                            ToolButton(R.string.editor_add_text, R.drawable.ic_tool_text, true, onAddText)
                            ToolButton(R.string.editor_subtitles, R.drawable.ic_subtitles, true, { onPanel(EditorPanel.SUBTITLES) }, active = activePanel == EditorPanel.SUBTITLES)
                            ToolButton(R.string.editor_add_image, R.drawable.ic_tool_image, true, onAddImage)
                            ToolButton(R.string.editor_add_pip, R.drawable.ic_tool_pip, tools.addPip, actions.onAddPip)
                        }
                        ToolButton(R.string.editor_edit_text, R.drawable.ic_tool_text, hasOverlay, { onPanel(EditorPanel.TEXT) }, active = activePanel == EditorPanel.TEXT)
                        ToolButton(R.string.editor_pip_layout, R.drawable.ic_tool_pip, tools.pipLayout, { onPanel(EditorPanel.PIP) }, active = activePanel == EditorPanel.PIP)
                    }
                }
                val effects = tools.filter || tools.transform || tools.transition || tools.reverse || tools.freeze || tools.freezeLength || (!clip && tools.canvas)
                if (effects) {
                    ToolGroup(R.string.editor_group_effects, showDivider = tools.moveEarlier || tools.moveLater) {
                        ToolButton(R.string.editor_filter, R.drawable.ic_tool_filter, tools.filter, { onPanel(EditorPanel.FILTER) }, active = activePanel == EditorPanel.FILTER)
                        ToolButton(R.string.editor_crop, R.drawable.ic_tool_crop, tools.transform, { onPanel(EditorPanel.CROP) }, active = activePanel == EditorPanel.CROP)
                        ToolButton(R.string.editor_transition, R.drawable.ic_tool_transition, tools.transition, { onPanel(EditorPanel.TRANSITION) }, active = activePanel == EditorPanel.TRANSITION)
                        ToolButton(R.string.editor_canvas, R.drawable.ic_tool_canvas, tools.canvas && !clip, { onPanel(EditorPanel.CANVAS) }, active = activePanel == EditorPanel.CANVAS)
                        ToolButton(R.string.editor_reverse, R.drawable.ic_tool_reverse, tools.reverse, actions.onReverse)
                        ToolButton(R.string.editor_freeze, R.drawable.ic_tool_freeze, tools.freeze, actions.onFreeze)
                        ToolButton(R.string.editor_freeze_length, R.drawable.ic_tool_freeze, tools.freezeLength, { onPanel(EditorPanel.FREEZE) }, active = activePanel == EditorPanel.FREEZE)
                    }
                }
                if (tools.moveEarlier || tools.moveLater) {
                    ToolGroup(R.string.editor_group_arrange, showDivider = false) {
                        ToolButton(R.string.editor_move_earlier, R.drawable.ic_tool_move_earlier, tools.moveEarlier, actions.onMoveEarlier)
                        ToolButton(R.string.editor_move_later, R.drawable.ic_tool_move_later, tools.moveLater, actions.onMoveLater)
                    }
                }
            }
        }
    }
}

/** A captioned run of related tools, followed by a hairline divider. */
@Composable
private fun ToolGroup(
    @androidx.annotation.StringRes title: Int,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                stringResource(title),
                Modifier.padding(start = 8.dp, bottom = 2.dp).semantics { heading() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
        if (showDivider) {
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(52.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

/** Icon-over-label tool: a rounded icon tile and a short label, 64 dp square at least; dimmed when it does not apply. */
@Composable
private fun ToolButton(
    @androidx.annotation.StringRes label: Int,
    @androidx.annotation.DrawableRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
    /** Step 23: a tool that does not apply is not shown at all; undo, redo and adding media stay visible (dimmed). */
    hideWhenOff: Boolean = true,
    /** Step 23: this tool's panel is open; the tile is lit and TalkBack says "selected". */
    active: Boolean = false,
) {
    if (!enabled && hideWhenOff) return
    val colors = MaterialTheme.colorScheme
    val brand = BConverterTheme.brand
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(if (active) Modifier.background(colors.primary.copy(alpha = 0.12f)).semantics { selected = true } else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .then(
                    when {
                        accent && enabled -> Modifier.background(brand.signature)
                        active -> Modifier.background(colors.primary.copy(alpha = 0.28f))
                        else -> Modifier.background(colors.surfaceContainerHighest)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp), tint = if (accent && enabled) Color.White else if (active) colors.primary else colors.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (active) colors.primary else colors.onSurface)
    }
}

/** A small round tonal button for the timeline zoom steps; [description] is what TalkBack reads. */
@Composable
private fun ZoomChip(glyph: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        // 36 dp drawn; Surface keeps the touch target at 48 dp (minimum interactive size).
        modifier = Modifier.size(36.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Text(glyph, style = MaterialTheme.typography.titleMedium) }
    }
}

/** A soft, enlarged still of the clip under the playhead behind the player: what the bars of a blurred background look like. Nothing is drawn until the frame arrives. */
@Composable
private fun BlurredBackdrop(state: EditorUiState, playheadMs: Long, media: TimelineMedia?) {
    val clip = state.previewTimeline.videoClipAt(playheadMs)
    val file = (clip?.source as? io.bsoft.bconverter.domain.model.MediaSource.FileSource)?.file
    val position = clip?.let { it.sourceStartMs + (playheadMs - it.startTimeMs).coerceIn(0L, maxOf(it.durationMs - 1, 0L)) } ?: 0L
    val frame by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, position / 1000) {
        value = if (file != null && media != null) media.frame(file, position, 160) else null
    }
    frame?.let {
        androidx.compose.foundation.Image(
            it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp),
        )
    }
}

/** The most clips the Photo Picker lets you add in one go; they are added in the order picked. */
private const val MAX_VIDEOS_PER_PICK = 20
