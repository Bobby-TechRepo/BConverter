package io.bsoft.bconverter.feature.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ExportPreset
import io.bsoft.bconverter.domain.timeline.Transition
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.feature.converter.Dropdown
import io.bsoft.bconverter.feature.converter.displayName
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_rename_project)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.editor_project_name)) }) },
        confirmButton = { TextButton({ onConfirm(text); onDismiss() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.editor_apply)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

/**
 * Picks the part of the source that the clip uses, as a panel inside the editor (step 23): the change is applied when a handle
 * is let go (one undo step), so the preview shows it at once. Both ends are in source time; the range is never empty. Done
 * also applies a change that has not been applied yet (one made with TalkBack's adjust actions, say).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimPanel(sourceDurationMs: Long, startMs: Long, endMs: Long, onApply: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    var range by remember(startMs, endMs) { mutableStateOf(startMs.toFloat()..endMs.toFloat()) }
    // Reads the state when called, so a release in the same frame as the last move still applies the final position.
    val applyIfChanged: () -> Unit = {
        val s = range.start.roundToLong()
        val e = range.endInclusive.roundToLong()
        if (e > s && (s != startMs || e != endMs)) onApply(s, e)
    }
    val startLabel = stringResource(R.string.editor_trim_start)
    val endLabel = stringResource(R.string.editor_trim_end)
    OverlayPanel(
        title = stringResource(R.string.editor_trim_title), onDismiss = onDismiss,
        actions = { PanelDoneButton { applyIfChanged(); onDismiss() } },
    ) {
        Text(
            stringResource(R.string.editor_trim_range, formatTime(range.start.roundToLong()), formatTime(range.endInclusive.roundToLong())),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..sourceDurationMs.toFloat(),
            onValueChangeFinished = applyIfChanged,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$startLabel, $endLabel" },
        )
        Text(stringResource(R.string.editor_trim_panel_hint), style = MaterialTheme.typography.bodySmall)
    }
}

/** Playback speed of the selected clip; a choice applies at once (one undo step) and the panel stays open to compare. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedPanel(current: Float, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    OverlayPanel(
        title = stringResource(R.string.editor_speed_title), onDismiss = onDismiss,
        actions = { PanelDoneButton(onDismiss) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SPEED_CHOICES.forEach { speed ->
                FilterChip(
                    selected = speed == current,
                    onClick = { if (speed != current) onApply(speed) },
                    label = { Text(stringResource(R.string.editor_clip_speed, formatSpeed(speed))) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** The transition into the selected clip: a kind applies at once, the length when the slider is let go (one undo step each). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransitionPanel(current: Transition, onApply: (TransitionType, Long) -> Unit, onDismiss: () -> Unit) {
    var seconds by remember(current.durationMs) {
        mutableFloatStateOf((current.durationMs / 1000f).coerceIn(TRANSITION_MIN_S, TRANSITION_MAX_S))
    }
    val durationLabel = stringResource(R.string.editor_transition_length)
    OverlayPanel(
        title = stringResource(R.string.editor_transition_title), onDismiss = onDismiss,
        actions = { PanelDoneButton(onDismiss) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TransitionType.NONE to R.string.editor_transition_none,
                TransitionType.FADE_BLACK to R.string.editor_transition_black,
                TransitionType.CROSSFADE to R.string.editor_transition_cross,
            ).forEach { (option, label) ->
                FilterChip(
                    selected = current.type == option,
                    onClick = { if (option != current.type) onApply(option, (seconds * 1000).roundToLong()) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (current.type != TransitionType.NONE) {
            Text(
                stringResource(R.string.editor_transition_seconds, TRANSITION_FORMAT.format(seconds)),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Slider(
                seconds, { seconds = (it * 4).roundToInt() / 4f }, valueRange = TRANSITION_MIN_S..TRANSITION_MAX_S, steps = 6,
                onValueChangeFinished = {
                    val ms = (seconds * 1000).roundToLong()
                    if (ms != current.durationMs) onApply(current.type, ms)
                },
                modifier = Modifier.semantics { contentDescription = durationLabel },
            )
            if (current.type == TransitionType.CROSSFADE) Text(stringResource(R.string.editor_transition_preview_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val TRANSITION_MIN_S = 0.25f
private const val TRANSITION_MAX_S = 2f
private val TRANSITION_FORMAT = java.text.DecimalFormat("0.##")

val SPEED_CHOICES = listOf(0.25f, 0.5f, 1f, 2f, 4f)

/** Level of the selected audio clip, applied when the slider is let go (one undo step). */
@Composable
fun VolumePanel(current: Float, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    var volume by remember(current) { mutableFloatStateOf(current) }
    val label = stringResource(R.string.editor_volume)
    OverlayPanel(
        title = stringResource(R.string.editor_volume_title), onDismiss = onDismiss,
        actions = { PanelDoneButton(onDismiss) },
    ) {
        Text(stringResource(R.string.editor_volume_value, (volume * 100).roundToInt()), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Slider(
            volume, { volume = it }, valueRange = 0f..2f,
            onValueChangeFinished = { if (volume != current) onApply(volume) },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@StringRes
fun ExportPreset.labelRes(): Int = when (this) {
    ExportPreset.SMALL_FILE -> R.string.preset_small_file
    ExportPreset.BALANCED -> R.string.preset_balanced
    ExportPreset.HIGH_QUALITY -> R.string.preset_high_quality
}

@StringRes
fun io.bsoft.bconverter.domain.model.SharePreset.labelRes(): Int = when (this) {
    io.bsoft.bconverter.domain.model.SharePreset.YOUTUBE -> R.string.share_youtube
    io.bsoft.bconverter.domain.model.SharePreset.VERTICAL_SHORTS -> R.string.share_vertical
    io.bsoft.bconverter.domain.model.SharePreset.INSTAGRAM_POST -> R.string.share_instagram_post
    io.bsoft.bconverter.domain.model.SharePreset.SQUARE -> R.string.share_square
    io.bsoft.bconverter.domain.model.SharePreset.WHATSAPP_STATUS -> R.string.share_whatsapp_status
}

@StringRes
fun io.bsoft.bconverter.domain.model.ExportSize.labelRes(): Int = when (this) {
    io.bsoft.bconverter.domain.model.ExportSize.AUTO -> R.string.export_size_auto
    io.bsoft.bconverter.domain.model.ExportSize.ORIGINAL -> R.string.export_size_original
    io.bsoft.bconverter.domain.model.ExportSize.P1080 -> R.string.export_size_1080
    io.bsoft.bconverter.domain.model.ExportSize.P720 -> R.string.export_size_720
    io.bsoft.bconverter.domain.model.ExportSize.P480 -> R.string.export_size_480
}

@Composable
fun ExportDialog(
    preset: ExportPreset,
    container: ContainerFormat,
    containers: List<ContainerFormat>,
    onPreset: (ExportPreset) -> Unit,
    onContainer: (ContainerFormat) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    /** Plain-language notes about subtitles in this export (what will happen, what will be lost). */
    notes: List<String> = emptyList(),
    canStart: Boolean = true,
    size: io.bsoft.bconverter.domain.model.ExportSize = io.bsoft.bconverter.domain.model.ExportSize.AUTO,
    onSize: (io.bsoft.bconverter.domain.model.ExportSize) -> Unit = {},
    onSharePreset: ((io.bsoft.bconverter.domain.model.SharePreset) -> Unit)? = null,
    /** The "made for" preset the current choices amount to, shown as picked. */
    sharePreset: io.bsoft.bconverter.domain.model.SharePreset? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (onSharePreset != null) {
                    val madeFor = stringResource(R.string.share_made_for)
                    Text(madeFor, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).semantics { contentDescription = madeFor },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        io.bsoft.bconverter.domain.model.SharePreset.entries.forEach { p ->
                            FilterChip(sharePreset == p, { onSharePreset(p) }, { Text(stringResource(p.labelRes())) }, Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Text(stringResource(R.string.share_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Dropdown(
                    label = stringResource(R.string.editor_export_quality),
                    selectedText = stringResource(preset.labelRes()),
                    options = ExportPreset.entries.map { stringResource(it.labelRes()) to it },
                    onSelect = onPreset,
                )
                Dropdown(
                    label = stringResource(R.string.editor_export_size),
                    selectedText = stringResource(size.labelRes()),
                    options = io.bsoft.bconverter.domain.model.ExportSize.entries.map { stringResource(it.labelRes()) to it },
                    onSelect = onSize,
                )
                Dropdown(
                    label = stringResource(R.string.editor_export_format),
                    selectedText = container.displayName,
                    options = containers.map { it.displayName to it },
                    onSelect = onContainer,
                )
                notes.forEach { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button({ onStart(); onDismiss() }, enabled = canStart) { Text(stringResource(R.string.editor_export_start)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}
