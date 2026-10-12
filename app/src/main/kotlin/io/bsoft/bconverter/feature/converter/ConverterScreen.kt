package io.bsoft.bconverter.feature.converter

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.feature.pickinput.PickKind
import io.bsoft.bconverter.feature.pickinput.inputErrorMessage
import kotlin.math.roundToInt

data class ConverterActions(
    val onSelectInput: () -> Unit = {},
    val onPreset: (ConversionPreset) -> Unit = {},
    val onContainer: (ContainerFormat) -> Unit = {},
    val onVideoCodec: (VideoCodec?) -> Unit = {},
    val onAudioCodec: (AudioCodec?) -> Unit = {},
    val onOutputName: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onConvertAnother: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Stateful entry point: system pickers (input: document picker, output: create-document) + ViewModel. */
@Composable
fun ConverterScreen(onBack: () -> Unit, viewModel: ConverterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onInputPicked(it) }
    val mimeType = state.form?.container?.mimeType ?: "video/*"
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
    BackHandler(enabled = state.stage == ConverterStage.Converting) { viewModel.onCancel() }
    ConverterContent(
        state = state,
        actions = ConverterActions(
            onSelectInput = { pickInput.launch(PickKind.VIDEO.mimeTypes.toTypedArray()) },
            onPreset = viewModel::onPresetSelected,
            onContainer = viewModel::onContainerSelected,
            onVideoCodec = viewModel::onVideoCodecSelected,
            onAudioCodec = viewModel::onAudioCodecSelected,
            onOutputName = viewModel::onOutputNameChanged,
            onStart = viewModel::onStartRequested,
            onCancel = viewModel::onCancel,
            onConvertAnother = viewModel::onConvertAnother,
            onBack = onBack,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterContent(state: ConverterUiState, actions: ConverterActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_convert_video)) }, navigationIcon = { BackButton(actions.onBack) })
        },
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
                ConverterStage.Converting -> Centered {
                    val fraction = (state.processing as? ProcessingState.Running)?.progress ?: 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.converter_converting, (fraction * 100).roundToInt()),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = actions.onCancel) { Text(stringResource(R.string.converter_cancel)) }
                }
                ConverterStage.Extracting -> Unit // only used by the audio extractor
                ConverterStage.Completed -> Centered {
                    Text(stringResource(R.string.converter_saved), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = actions.onConvertAnother) { Text(stringResource(R.string.converter_convert_another)) }
                }
            }
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigureForm(state: ConverterUiState, actions: ConverterActions) {
    val form = state.form ?: return
    val source = state.source ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SourceCard(source)
        state.message?.let { MessageText(it) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConversionPreset.entries.forEach { preset ->
                FilterChip(
                    selected = form.preset == preset,
                    onClick = { actions.onPreset(preset) },
                    label = { Text(stringResource(preset.labelRes())) },
                )
            }
        }

        Dropdown(
            label = stringResource(R.string.converter_container),
            selectedText = form.container.displayName,
            options = state.containers.map { it.displayName to it },
            onSelect = actions.onContainer,
        )
        val automatic = stringResource(R.string.converter_automatic)
        Dropdown(
            label = stringResource(R.string.converter_video_codec),
            selectedText = form.videoCodec?.displayName ?: automatic,
            options = listOf<Pair<String, VideoCodec?>>(automatic to null) + state.videoCodecs.map { it.displayName to it },
            onSelect = actions.onVideoCodec,
        )
        if (source.metadata.hasAudio) {
            Dropdown(
                label = stringResource(R.string.converter_audio_codec),
                selectedText = form.audioCodec?.displayName ?: automatic,
                options = listOf<Pair<String, AudioCodec?>>(automatic to null) + state.audioCodecs.map { it.displayName to it },
                onSelect = actions.onAudioCodec,
            )
        }
        OutlinedTextField(
            value = form.outputName,
            onValueChange = actions.onOutputName,
            label = { Text(stringResource(R.string.converter_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.issues.forEach { issue ->
            Text(
                stringResource(issue.messageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        if (state.settings == null) Text(stringResource(R.string.converter_no_settings), color = MaterialTheme.colorScheme.error)
        Button(onClick = actions.onStart, enabled = state.settings != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.converter_convert))
        }
    }
}

@Composable
internal fun SourceCard(source: SourceInfo) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.converter_unknown)
    val m = source.metadata
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(source.displayName, style = MaterialTheme.typography.titleMedium)
            Text(Formatter.formatFileSize(context, source.sizeBytes), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.converter_summary_duration, formatDuration(m.durationMs)))
            m.container?.let { Text(stringResource(R.string.converter_summary_container, it.displayName)) }
            m.videoStreams.firstOrNull()?.let {
                Text(stringResource(R.string.converter_summary_video, it.codec ?: unknown, it.displayWidth, it.displayHeight))
            }
            m.audioStreams.firstOrNull()?.let { Text(stringResource(R.string.converter_summary_audio, it.codec ?: unknown)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Dropdown(label: String, selectedText: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
internal fun MessageText(message: ConverterMessage) {
    Text(
        text = when (message) {
            is ConverterMessage.InputProblem -> inputErrorMessage(message.error)
            ConverterMessage.ProbeFailed -> stringResource(R.string.msg_probe_failed)
            ConverterMessage.NoVideoStream -> stringResource(R.string.msg_no_video)
            ConverterMessage.NoAudioStream -> stringResource(R.string.msg_no_audio)
            ConverterMessage.NoEncodersAvailable -> stringResource(R.string.msg_no_encoders)
            ConverterMessage.ConversionFailed -> stringResource(R.string.msg_conversion_failed)
            ConverterMessage.SaveFailed -> stringResource(R.string.msg_save_failed)
        },
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@StringRes
private fun ConversionPreset.labelRes(): Int = when (this) {
    ConversionPreset.SMALL_FILE -> R.string.preset_small_file
    ConversionPreset.BALANCED -> R.string.preset_balanced
    ConversionPreset.HIGH_QUALITY -> R.string.preset_high_quality
    ConversionPreset.ORIGINAL_QUALITY -> R.string.preset_original_quality
}

@StringRes
internal fun ConversionIssue.messageRes(): Int = when (this) {
    ConversionIssue.SourceHasNoVideo -> R.string.issue_no_video
    ConversionIssue.SourceHasNoAudio -> R.string.issue_no_audio
    is ConversionIssue.CodecNotAllowedInContainer -> R.string.issue_codec_container
    is ConversionIssue.EncoderUnavailable -> R.string.issue_encoder_unavailable
    is ConversionIssue.CrfNotSupported -> R.string.issue_crf_unsupported
    is ConversionIssue.CrfOutOfRange -> R.string.issue_crf_range
    ConversionIssue.InvalidVideoBitrate -> R.string.issue_video_bitrate
    ConversionIssue.InvalidResolution -> R.string.issue_resolution
    ConversionIssue.InvalidFrameRate -> R.string.issue_frame_rate
    ConversionIssue.VideoCopyNotPossible -> R.string.issue_video_copy
    ConversionIssue.AudioCopyNotPossible -> R.string.issue_audio_copy
    is ConversionIssue.AudioBitrateNotAllowed -> R.string.issue_audio_bitrate_not_allowed
    ConversionIssue.InvalidAudioBitrate -> R.string.issue_audio_bitrate
    ConversionIssue.InvalidSampleRate -> R.string.issue_sample_rate
    is ConversionIssue.InvalidChannels -> R.string.issue_channels
    ConversionIssue.InvalidOutputName -> R.string.issue_output_name
    ConversionIssue.InvalidAudioStream -> R.string.issue_audio_stream
}
