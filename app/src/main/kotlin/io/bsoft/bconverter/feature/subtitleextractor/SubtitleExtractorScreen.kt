package io.bsoft.bconverter.feature.subtitleextractor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.CompletedPanel
import io.bsoft.bconverter.core.ui.ErrorBanner
import io.bsoft.bconverter.core.ui.PrimaryButton
import io.bsoft.bconverter.core.ui.ProgressPanel
import io.bsoft.bconverter.core.ui.SectionLabel
import io.bsoft.bconverter.core.ui.SelectInputPanel
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.core.ui.rememberShareAction
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.model.SubtitleKind
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream
import io.bsoft.bconverter.domain.usecase.SubtitleExtractionPlanner
import io.bsoft.bconverter.feature.converter.Centered
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.Dropdown
import io.bsoft.bconverter.feature.converter.MessageText
import io.bsoft.bconverter.feature.converter.SourceCard
import io.bsoft.bconverter.feature.converter.messageText
import io.bsoft.bconverter.feature.pickinput.PickKind
import kotlin.math.roundToInt

data class SubtitleExtractorActions(
    val onSelectInput: () -> Unit = {},
    val onToggle: (Int, Boolean) -> Unit = { _, _ -> },
    val onOutput: (Int, SubtitleOutput) -> Unit = { _, _ -> },
    val onBaseName: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onExtractAnother: () -> Unit = {},
    val onShare: (SavedOutput) -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun SubtitleExtractorScreen(
    onBack: () -> Unit,
    /** A video another app shared with BConverter (step 25); it is opened once. */
    sharedInput: android.net.Uri? = null,
    viewModel: SubtitleExtractorViewModel = hiltViewModel(),
) {
    val share = rememberShareAction()
    val state by viewModel.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(sharedInput) { sharedInput?.let(viewModel::onInputPicked) }
    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onInputPicked(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onFolderChosen(it) }
    BackHandler(enabled = state.stage == ConverterStage.Extracting) { viewModel.onCancel() }
    SubtitleExtractorContent(
        state = state,
        actions = SubtitleExtractorActions(
            onSelectInput = { pickInput.launch(PickKind.VIDEO.mimeTypes.toTypedArray()) },
            onToggle = viewModel::onTrackToggled,
            onOutput = viewModel::onOutputSelected,
            onBaseName = viewModel::onBaseNameChanged,
            onStart = { if (viewModel.onStartRequested()) pickFolder.launch(null) },
            onCancel = viewModel::onCancel,
            onExtractAnother = viewModel::onExtractAnother,
            onShare = share,
            onBack = onBack,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleExtractorContent(state: SubtitleExtractorUiState, actions: SubtitleExtractorActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_extract_subtitles)) }, navigationIcon = { BackButton(actions.onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).readableWidth()) {
            when (state.stage) {
                ConverterStage.SelectInput -> SelectInputPanel(
                    icon = R.drawable.ic_subtitles,
                    hint = stringResource(R.string.select_video_hint_subtitles),
                    buttonText = stringResource(R.string.select_video),
                    error = state.message?.let { messageText(it) },
                    onSelect = actions.onSelectInput,
                )
                ConverterStage.Probing -> Centered {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.converter_analyzing), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                ConverterStage.Configure -> ConfigureForm(state, actions)
                ConverterStage.Extracting -> {
                    ProgressPanel(
                        icon = R.drawable.ic_subtitles,
                        fraction = state.progress,
                        label = stringResource(R.string.subtitle_extracting, state.currentFile + 1, state.totalFiles, (state.progress * 100).roundToInt()),
                        cancelText = stringResource(R.string.converter_cancel),
                        onCancel = actions.onCancel,
                    )
                }
                ConverterStage.Completed -> CompletedPanel(
                    message = stringResource(R.string.subtitle_saved, state.savedCount) +
                        if (state.failedCount > 0) "\n" + stringResource(R.string.subtitle_some_failed, state.failedCount) else "",
                    saved = state.saved,
                    onShare = actions.onShare,
                    anotherText = stringResource(R.string.subtitle_extract_another),
                    onAnother = actions.onExtractAnother,
                )
                ConverterStage.Converting -> Unit
            }
        }
    }
}

@Composable
private fun ConfigureForm(state: SubtitleExtractorUiState, actions: SubtitleExtractorActions) {
    val source = state.source ?: return
    val planner = SubtitleExtractionPlanner()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SourceCard(source)
        state.message?.let { MessageText(it) }
        SectionLabel(stringResource(R.string.subtitle_tracks))
        source.metadata.subtitleStreams.forEachIndexed { i, stream ->
            TrackRow(i, stream, state.selection[i], planner, actions)
        }
        if (source.metadata.subtitleStreams.any { it.kind == SubtitleKind.IMAGE }) {
            Text(stringResource(R.string.subtitle_image_hint), style = MaterialTheme.typography.bodySmall)
        }
        SectionLabel(stringResource(R.string.section_output))
        OutlinedTextField(
            value = state.baseName,
            onValueChange = actions.onBaseName,
            label = { Text(stringResource(R.string.converter_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.issues.forEach { ErrorBanner(it.messageText()) }
        PrimaryButton(stringResource(R.string.subtitle_extract), actions.onStart, enabled = state.selection.isNotEmpty())
    }
}

@Composable
private fun TrackRow(ordinal: Int, stream: SubtitleStream, chosen: SubtitleOutput?, planner: SubtitleExtractionPlanner, actions: SubtitleExtractorActions) {
    val outputs = planner.outputsFor(stream)
    val supported = outputs.isNotEmpty()
    val label = trackLabel(ordinal, stream)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = chosen != null, enabled = supported, role = Role.Checkbox, onValueChange = { actions.onToggle(ordinal, it) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = chosen != null, onCheckedChange = null, enabled = supported)
            Column(Modifier.padding(start = 8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (supported) stringResource(if (stream.kind == SubtitleKind.IMAGE) R.string.subtitle_kind_image else R.string.subtitle_kind_text)
                    else stringResource(R.string.subtitle_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (chosen != null && outputs.size > 1) {
            Dropdown(
                label = stringResource(R.string.extractor_format),
                selectedText = chosen.displayLabel(),
                options = outputs.map { it.displayLabel() to it },
                onSelect = { actions.onOutput(ordinal, it) },
            )
            if (planner.losesStyling(stream, chosen)) Text(stringResource(R.string.subtitle_style_lost), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun SubtitleOutput.displayLabel() = when (this) {
    SubtitleOutput.SRT -> "SRT (.srt)"
    SubtitleOutput.ASS -> "ASS (.ass)"
    SubtitleOutput.WEBVTT -> "WebVTT (.vtt)"
    SubtitleOutput.SUP -> "PGS (.sup)"
    SubtitleOutput.MKS -> "Matroska (.mks)"
}

@Composable
private fun trackLabel(ordinal: Int, stream: SubtitleStream): String {
    val parts = listOfNotNull(
        stream.title?.takeIf { it.isNotBlank() },
        stream.language,
        stream.codec,
        stringResource(R.string.subtitle_tag_default).takeIf { stream.isDefault },
        stringResource(R.string.subtitle_tag_forced).takeIf { stream.isForced },
    )
    return stringResource(R.string.subtitle_track_item, ordinal + 1, parts.joinToString(" · "))
}
