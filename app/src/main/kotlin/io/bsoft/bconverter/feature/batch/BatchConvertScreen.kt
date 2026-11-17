package io.bsoft.bconverter.feature.batch

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.GradientLinearProgress
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.PrimaryButton
import io.bsoft.bconverter.core.ui.QuietButton
import io.bsoft.bconverter.core.ui.SectionLabel
import io.bsoft.bconverter.core.ui.SelectInputPanel
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.core.ui.rememberShareAction
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.usecase.BatchItem
import io.bsoft.bconverter.domain.usecase.BatchItemStatus
import io.bsoft.bconverter.domain.usecase.BatchRun
import io.bsoft.bconverter.domain.usecase.BatchSkip
import io.bsoft.bconverter.feature.converter.displayName
import io.bsoft.bconverter.feature.converter.labelRes
import io.bsoft.bconverter.feature.export.RequestNotificationPermissionWhen
import io.bsoft.bconverter.feature.pickinput.PickKind
import kotlin.math.roundToInt

data class BatchActions(
    val onBack: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onRemove: (Uri) -> Unit = {},
    val onPreset: (ConversionPreset) -> Unit = {},
    val onContainer: (ContainerFormat) -> Unit = {},
    /** Asks for the folder the videos are saved into; the batch starts when one is chosen. */
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onConvertMore: () -> Unit = {},
    val onShare: (SavedOutput) -> Unit = {},
)

/**
 * Converting several videos at once (implementation plan step 25). [sharedInputs] are videos another app shared with
 * BConverter; they are added to the list once.
 */
@Composable
fun BatchConvertScreen(onBack: () -> Unit, sharedInputs: List<Uri>? = null, viewModel: BatchConvertViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val share = rememberShareAction()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.onPicked(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onFolderChosen(it) }
    LaunchedEffect(sharedInputs) { sharedInputs?.let(viewModel::onPicked) }
    RequestNotificationPermissionWhen(state.run == null && state.picked.isNotEmpty())
    BatchConvertContent(
        state,
        BatchActions(
            onBack = onBack,
            onPick = { pick.launch(PickKind.VIDEO.mimeTypes.toTypedArray()) },
            onRemove = viewModel::onRemove,
            onPreset = viewModel::onPreset,
            onContainer = viewModel::onContainer,
            onStart = { folder.launch(null) },
            onCancel = viewModel::onCancel,
            onConvertMore = viewModel::onConvertMore,
            onShare = share,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchConvertContent(state: BatchUiState, actions: BatchActions) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.batch_title)) }, navigationIcon = { BackButton(actions.onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).readableWidth()) {
            val run = state.run
            when {
                run != null -> RunList(run, actions)
                state.picked.isEmpty() -> SelectInputPanel(
                    icon = R.drawable.ic_convert_video,
                    hint = stringResource(R.string.batch_select_hint),
                    buttonText = stringResource(R.string.batch_choose),
                    error = null,
                    onSelect = actions.onPick,
                )
                else -> PickList(state, actions)
            }
        }
    }
}

// ---- before the batch: the list and the shared settings ----
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickList(state: BatchUiState, actions: BatchActions) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.batch_video_count, state.picked.size, state.picked.size),
                    Modifier.weight(1f).semantics { heading(); liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                )
                QuietButton(stringResource(R.string.batch_add_more), actions.onPick, enabled = state.picked.size < BatchConvertViewModel.MAX_FILES)
            }
        }
        itemsIndexed(state.picked, key = { _, p -> p.uri.toString() }) { _, file ->
            val name = file.name ?: file.uri.lastPathSegment ?: file.uri.toString()
            PremiumCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        file.sizeBytes?.let { Text(Formatter.formatShortFileSize(context, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val remove = stringResource(R.string.batch_remove, name)
                    IconButton({ actions.onRemove(file.uri) }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = remove) }
                }
            }
        }
        item {
            SectionLabel(stringResource(R.string.section_preset))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConversionPreset.entries.forEach { p ->
                    FilterChip(state.options.preset == p, { actions.onPreset(p) }, { Text(stringResource(p.labelRes())) }, Modifier.heightIn(min = 48.dp))
                }
            }
            SectionLabel(stringResource(R.string.section_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.containers.forEach { c ->
                    FilterChip(state.options.container == c, { actions.onContainer(c) }, { Text(c.displayName) }, Modifier.heightIn(min = 48.dp))
                }
            }
            Text(stringResource(R.string.batch_codecs_note), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(pluralStringResource(R.plurals.batch_start, state.picked.size, state.picked.size), actions.onStart, enabled = state.canStart)
                Text(stringResource(R.string.batch_folder_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- during and after the batch ----
@Composable
private fun RunList(run: BatchRun, actions: BatchActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (run.running) {
                    val current = (run.currentIndex ?: run.items.count { it.status.isFinal }).coerceAtMost(run.items.size - 1) + 1
                    Text(stringResource(R.string.batch_converting, current, run.items.size), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    GradientLinearProgress(run.progress)
                    Text(stringResource(R.string.batch_leave_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton(stringResource(R.string.converter_cancel), actions.onCancel)
                } else {
                    Text(stringResource(R.string.batch_done_summary, run.savedCount, run.items.size - run.savedCount), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    PrimaryButton(stringResource(R.string.batch_more), actions.onConvertMore)
                }
            }
        }
        itemsIndexed(run.items, key = { i, item -> "$i-${item.uri}" }) { _, item -> RunRow(item, actions) }
    }
}

@Composable
private fun RunRow(item: BatchItem, actions: BatchActions) {
    val name = item.name ?: item.uri.lastPathSegment ?: item.uri.toString()
    val status = item.status
    PremiumCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                val problem = status is BatchItemStatus.Skipped || status is BatchItemStatus.Failed
                Text(
                    statusText(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status is BatchItemStatus.Converting) GradientLinearProgress(status.progress)
            }
            if (status is BatchItemStatus.Saved) TextButton({ actions.onShare(status.output) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.share)) }
        }
    }
}

@Composable
private fun statusText(status: BatchItemStatus): String = when (status) {
    BatchItemStatus.Waiting -> stringResource(R.string.batch_status_waiting)
    BatchItemStatus.Preparing -> stringResource(R.string.batch_status_preparing)
    is BatchItemStatus.Converting -> stringResource(R.string.batch_status_converting, (status.progress * 100).roundToInt())
    is BatchItemStatus.Saved -> stringResource(R.string.batch_status_saved, status.fileName)
    BatchItemStatus.Cancelled -> stringResource(R.string.batch_status_cancelled)
    is BatchItemStatus.Skipped -> stringResource(
        when (status.reason) {
            BatchSkip.UNREADABLE -> R.string.batch_skip_unreadable
            BatchSkip.NO_VIDEO -> R.string.batch_skip_no_video
            BatchSkip.NOT_CONVERTIBLE -> R.string.batch_skip_not_convertible
            BatchSkip.NO_SPACE -> R.string.batch_skip_no_space
            BatchSkip.CANNOT_SAVE -> R.string.batch_skip_cannot_save
        },
    )
    is BatchItemStatus.Failed -> stringResource(
        when (status.reason) {
            JobFailure.SaveFailed -> R.string.batch_failed_save
            JobFailure.Interrupted -> R.string.batch_failed_interrupted
            JobFailure.ConversionFailed, JobFailure.OverlaysFailed -> R.string.batch_failed
        },
    )
}
