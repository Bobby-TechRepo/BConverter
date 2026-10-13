package io.bsoft.bconverter.feature.audioextractor

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.feature.converter.ConverterEvent
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.Centered
import io.bsoft.bconverter.feature.converter.Dropdown
import io.bsoft.bconverter.feature.converter.MessageText
import io.bsoft.bconverter.feature.converter.SourceCard
import io.bsoft.bconverter.feature.converter.displayName
import io.bsoft.bconverter.feature.converter.messageText
import io.bsoft.bconverter.feature.export.RequestNotificationPermissionWhen
import io.bsoft.bconverter.feature.pickinput.PickKind
import io.bsoft.bconverter.feature.preview.PreviewPlayer
import kotlin.math.roundToInt

data class ExtractorActions(
    val onSelectInput: () -> Unit = {},
    val onStream: (Int) -> Unit = {},
    val onFormat: (AudioFormat) -> Unit = {},
    val onPreferCopy: (Boolean) -> Unit = {},
    val onOutputName: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onExtractAnother: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Stateful entry point: system pickers (input: document picker, output: create-document) + ViewModel. */
@Composable
fun AudioExtractorScreen(onBack: () -> Unit, viewModel: AudioExtractorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onInputPicked(it) }
    val mimeType = state.form?.format?.mimeType ?: "audio/*"
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) {
        viewModel.onDestinationChosen(it)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ConverterEvent.RequestDestination -> pickDestination.launch(event.fileName)
            }
        }
    }
    RequestNotificationPermissionWhen(state.stage == ConverterStage.Configure)
    BackHandler(enabled = state.stage == ConverterStage.Extracting) { viewModel.onCancel() }
    AudioExtractorContent(
        state = state,
        actions = ExtractorActions(
            onSelectInput = { pickInput.launch(PickKind.VIDEO.mimeTypes.toTypedArray()) },
            onStream = viewModel::onStreamSelected,
            onFormat = viewModel::onFormatSelected,
            onPreferCopy = viewModel::onPreferCopyChanged,
            onOutputName = viewModel::onOutputNameChanged,
            onStart = viewModel::onStartRequested,
            onCancel = viewModel::onCancel,
            onExtractAnother = viewModel::onExtractAnother,
            onBack = onBack,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioExtractorContent(state: AudioExtractorUiState, actions: ExtractorActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_extract_audio)) }, navigationIcon = { BackButton(actions.onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state.stage) {
                ConverterStage.SelectInput -> Centered {
                    state.message?.let { MessageText(it) }
                    Button(onClick = actions.onSelectInput) { Text(stringResource(R.string.select_video)) }
                }
                ConverterStage.Probing -> Centered {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.converter_analyzing), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                ConverterStage.Configure -> ConfigureForm(state, actions)
                ConverterStage.Extracting -> Centered {
                    val fraction = (state.processing as? ProcessingState.Running)?.progress ?: 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.extractor_extracting, (fraction * 100).roundToInt()),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = actions.onCancel) { Text(stringResource(R.string.converter_cancel)) }
                }
                ConverterStage.Completed -> Centered {
                    Text(stringResource(R.string.extractor_saved), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = actions.onExtractAnother) { Text(stringResource(R.string.extractor_extract_another)) }
                }
                ConverterStage.Converting -> Unit // only used by the video converter
            }
        }
    }
}

@Composable
private fun ConfigureForm(state: AudioExtractorUiState, actions: ExtractorActions) {
    val form = state.form ?: return
    val source = state.source ?: return
    val unknown = stringResource(R.string.converter_unknown)
    val trackOptions = source.metadata.audioStreams.mapIndexed { i, stream ->
        stringResource(R.string.extractor_track_item, i + 1, stream.codec ?: unknown) to i
    }
    val copyChecked = form.preferCopy && state.canCopy
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        source.previewFile?.let { PreviewPlayer(it) }
        SourceCard(source)
        state.message?.let { MessageText(it) }
        if (trackOptions.size > 1) {
            Dropdown(
                label = stringResource(R.string.extractor_track),
                selectedText = trackOptions.getOrNull(form.streamOrdinal)?.first ?: "",
                options = trackOptions,
                onSelect = actions.onStream,
            )
        }
        Dropdown(
            label = stringResource(R.string.extractor_format),
            selectedText = form.format.displayName,
            options = state.formats.map { it.displayName to it },
            onSelect = actions.onFormat,
        )
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = copyChecked, enabled = state.canCopy, role = Role.Switch, onValueChange = actions.onPreferCopy,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.extractor_copy), Modifier.weight(1f))
            Switch(checked = copyChecked, onCheckedChange = null, enabled = state.canCopy)
        }
        if (!state.canCopy) Text(stringResource(R.string.extractor_reencode_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = form.outputName,
            onValueChange = actions.onOutputName,
            label = { Text(stringResource(R.string.converter_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.issues.forEach { issue ->
            Text(
                issue.messageText(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Button(onClick = actions.onStart, enabled = state.settings != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.extractor_extract))
        }
    }
}
