package io.bsoft.bconverter.feature.jobs

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.BrandBackdrop
import io.bsoft.bconverter.core.ui.ErrorBanner
import io.bsoft.bconverter.core.ui.GradientLinearProgress
import io.bsoft.bconverter.core.ui.IconBadge
import io.bsoft.bconverter.core.ui.IconTile
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.QuietButton
import io.bsoft.bconverter.core.ui.SectionLabel
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.core.ui.shareIntent
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome

@Composable
fun JobsRoute(onBack: () -> Unit, vm: JobsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    JobsScreen(state, onBack, onCancel = vm::cancel, onShare = { shareResult(context, it) }, onClearFinished = vm::clearFinished)
}

/**
 * Exports (step 17): every export this app started, newest first. Running ones show live progress and can be cancelled;
 * finished ones can be shared. The list survives the app being closed; jobs that were running then show as interrupted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    state: JobsUiState,
    onBack: () -> Unit,
    onCancel: (String) -> Unit,
    onShare: (JobItem) -> Unit,
    onClearFinished: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(stringResource(R.string.home_jobs)) },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (state.finished.isNotEmpty()) TextButton(onClearFinished) { Text(stringResource(R.string.jobs_clear_finished)) }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.loadFailed -> ErrorBanner(stringResource(R.string.projects_load_failed), Modifier.align(Alignment.Center).padding(24.dp))
                    state.isEmpty -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(R.drawable.ic_jobs, size = 96.dp)
                        Text(
                            stringResource(R.string.jobs_empty), Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(
                        Modifier.readableWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.active.isNotEmpty()) {
                            item(key = "active-label") { SectionLabel(stringResource(R.string.jobs_section_active)) }
                            items(state.active, key = { it.id }) { JobRow(it, onCancel = { onCancel(it.id) }, onShare = { onShare(it) }) }
                        }
                        if (state.finished.isNotEmpty()) {
                            item(key = "finished-label") { SectionLabel(stringResource(R.string.jobs_section_recent)) }
                            items(state.finished, key = { it.id }) { JobRow(it, onCancel = { onCancel(it.id) }, onShare = { onShare(it) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(item: JobItem, onCancel: () -> Unit, onShare: () -> Unit) {
    val brand = BConverterTheme.brand
    PremiumCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(item.kind.iconRes())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(item.kind.labelRes()) + " · " + relativeTime(item.finishedAtMs ?: item.startedAtMs),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val phase = item.phase
            val statusColor = when {
                phase == JobPhase.Ended(JobOutcome.COMPLETED) -> brand.success
                phase is JobPhase.Ended && phase.outcome != JobOutcome.CANCELLED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                phaseText(phase), Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium, color = statusColor,
            )
            when (phase) {
                is JobPhase.Running -> if (phase.progress != null) GradientLinearProgress(phase.progress, Modifier.fillMaxWidth().padding(top = 8.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                JobPhase.Queued, JobPhase.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                is JobPhase.Ended -> Unit
            }
            if (item.isActive || item.canShare) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    if (item.isActive && phase != JobPhase.Saving) QuietButton(stringResource(R.string.jobs_cancel), onCancel)
                    if (item.canShare) QuietButton(stringResource(R.string.jobs_share), onShare)
                }
            }
        }
    }
}

@Composable
private fun phaseText(phase: JobPhase): String = when (phase) {
    JobPhase.Queued -> stringResource(R.string.jobs_queued)
    is JobPhase.Running -> phase.progress?.let { stringResource(R.string.jobs_running, (it * 100).toInt().coerceIn(0, 100)) }
        ?: stringResource(R.string.jobs_running_indeterminate)
    JobPhase.Saving -> stringResource(R.string.jobs_saving)
    is JobPhase.Ended -> stringResource(
        when (phase.outcome) {
            JobOutcome.COMPLETED -> R.string.jobs_completed
            JobOutcome.FAILED -> R.string.jobs_failed
            JobOutcome.SAVE_FAILED -> R.string.jobs_save_failed
            JobOutcome.CANCELLED -> R.string.jobs_cancelled
            JobOutcome.INTERRUPTED, JobOutcome.RUNNING -> R.string.jobs_interrupted
        },
    )
}

private fun relativeTime(atMs: Long): String =
    DateUtils.getRelativeTimeSpanString(atMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@DrawableRes
private fun JobKind.iconRes(): Int = when (this) {
    JobKind.CONVERT -> R.drawable.ic_convert_video
    JobKind.EXTRACT_AUDIO -> R.drawable.ic_extract_audio
    JobKind.EXTRACT_SUBTITLES -> R.drawable.ic_subtitles
    JobKind.RENDER -> R.drawable.ic_video_editor
}

@StringRes
private fun JobKind.labelRes(): Int = when (this) {
    JobKind.CONVERT -> R.string.home_convert_video
    JobKind.EXTRACT_AUDIO -> R.string.home_extract_audio
    JobKind.EXTRACT_SUBTITLES -> R.string.home_extract_subtitles
    JobKind.RENDER -> R.string.home_video_editor
}

/** Shares the saved file by its content Uri; its type is asked of the provider at share time (it is not stored). */
private fun shareResult(context: Context, item: JobItem) {
    val uri = Uri.parse(item.destination)
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"
    try {
        context.startActivity(shareIntent(SavedOutput(uri, mime), context.getString(R.string.share_chooser_title)))
    } catch (_: ActivityNotFoundException) {
        // Nothing can receive it; the file is still where the user saved it.
    } catch (_: SecurityException) {
        // The saved file was moved or deleted, or access to it was revoked.
    }
}
