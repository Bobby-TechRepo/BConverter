package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.subtitle.SubtitleAlignment
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleEditor
import io.bsoft.bconverter.domain.subtitle.SubtitleExportMode
import io.bsoft.bconverter.domain.subtitle.SubtitleStylePresets
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack

/** Callbacks for the subtitle dialogs; the route wires them to the ViewModel. */
data class SubtitleActions(
    val onImport: () -> Unit = {},
    val onAddTrack: () -> Unit = {},
    val onRemoveTrack: (String) -> Unit = {},
    val onRenameTrack: (String, String) -> Unit = { _, _ -> },
    val onOffset: (String, Long) -> Unit = { _, _ -> },
    val onAddCue: (String) -> Unit = {},
    val onSelectCue: (String?, String?) -> Unit = { _, _ -> },
    val onEditText: (String, String, String) -> Unit = { _, _, _ -> },
    val onRetime: (String, String, Long, Long) -> Unit = { _, _, _, _ -> },
    val onMoveCue: (String, String, Long) -> Unit = { _, _, _ -> },
    val onDuplicate: (String, String) -> Unit = { _, _ -> },
    val onSplit: (String, String) -> Unit = { _, _ -> },
    val onMerge: (String, String) -> Unit = { _, _ -> },
    val onDeleteCue: (String, String) -> Unit = { _, _ -> },
    val onExportMode: (String, io.bsoft.bconverter.domain.subtitle.SubtitleExportMode) -> Unit = { _, _ -> },
    val onLanguage: (String, String) -> Unit = { _, _ -> },
    val onDefault: (String, Boolean) -> Unit = { _, _ -> },
    val onForced: (String, Boolean) -> Unit = { _, _ -> },
    val onStyle: (String, io.bsoft.bconverter.domain.subtitle.SubtitleStyle) -> Unit = { _, _ -> },
)

/**
 * The subtitle tracks of the project, as a panel inside the editor (step 23): import a file, add an empty track, name, shift
 * or remove each track, add a cue. Adding a cue opens it for editing on top; closing that comes back here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitlesPanel(tracks: List<SubtitleTrack>, actions: SubtitleActions, onDismiss: () -> Unit) {
    OverlayPanel(
        title = stringResource(R.string.subtitles_title), onDismiss = onDismiss,
        actions = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.subtitles_done)) } },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(actions.onImport, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.subtitles_import)) }
            OutlinedButton(actions.onAddTrack, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.subtitles_new_track)) }
        }
        if (tracks.isEmpty()) Text(stringResource(R.string.subtitles_none), style = MaterialTheme.typography.bodyMedium)
        tracks.forEachIndexed { index, track ->
            if (index > 0) HorizontalDivider()
            TrackSection(track, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackSection(track: SubtitleTrack, actions: SubtitleActions) {
    var name by remember(track.id) { mutableStateOf(track.name) }
    var offset by remember(track.id, track.offsetMs) { mutableStateOf(SubtitleTimeInput.formatSigned(track.offsetMs)) }
    val parsedOffset = SubtitleTimeInput.parseSigned(offset)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            name, { name = it; if (it.isNotBlank()) actions.onRenameTrack(track.id, it) },
            singleLine = true, label = { Text(stringResource(R.string.subtitles_track_name)) }, modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.subtitles_cue_count, track.cues.size), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                offset, { offset = it }, singleLine = true, isError = parsedOffset == null,
                label = { Text(stringResource(R.string.subtitles_offset)) }, modifier = Modifier.weight(1f),
            )
            TextButton({ parsedOffset?.let { actions.onOffset(track.id, it) } }, enabled = parsedOffset != null && parsedOffset != track.offsetMs) {
                Text(stringResource(R.string.editor_apply))
            }
        }
        Text(stringResource(R.string.subtitles_export_as), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SubtitleExportMode.SOFT to R.string.subtitles_export_soft,
                SubtitleExportMode.BURNED to R.string.subtitles_export_burned,
                SubtitleExportMode.OFF to R.string.subtitles_export_off,
            ).forEach { (mode, label) ->
                FilterChip(selected = track.exportMode == mode, onClick = { actions.onExportMode(track.id, mode) }, label = { Text(stringResource(label)) })
            }
        }
        if (track.exportMode == SubtitleExportMode.SOFT) {
            var language by remember(track.id) { mutableStateOf(track.language.orEmpty()) }
            OutlinedTextField(
                language, { language = it; actions.onLanguage(track.id, it) }, singleLine = true,
                label = { Text(stringResource(R.string.subtitles_language)) }, modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = track.isDefault, onClick = { actions.onDefault(track.id, !track.isDefault) }, label = { Text(stringResource(R.string.subtitle_tag_default)) })
                FilterChip(selected = track.isForced, onClick = { actions.onForced(track.id, !track.isForced) }, label = { Text(stringResource(R.string.subtitle_tag_forced)) })
            }
        }
        var showAppearance by remember(track.id) { mutableStateOf(false) }
        if (showAppearance) AppearanceDialog(track, actions) { showAppearance = false }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ showAppearance = true }) { Text(stringResource(R.string.subtitles_appearance)) }
            OutlinedButton({ actions.onAddCue(track.id) }) { Text(stringResource(R.string.subtitles_add_cue)) }
            TextButton({ actions.onRemoveTrack(track.id) }) { Text(stringResource(R.string.subtitles_remove_track), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * Edits one cue, as a panel inside the editor (step 23) so the line can be read against the picture: text and times (stored
 * times, before the track offset), plus split at the playhead, merge, duplicate and delete. Text and times are applied
 * together with Apply; Cancel (or Back) leaves the cue as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CuePanel(track: SubtitleTrack, cue: SubtitleCue, actions: SubtitleActions, onDismiss: () -> Unit) {
    var text by remember(cue.id) { mutableStateOf(cue.text) }
    var start by remember(cue.id, cue.startMs) { mutableStateOf(SubtitleTimeInput.format(cue.startMs)) }
    var end by remember(cue.id, cue.endMs) { mutableStateOf(SubtitleTimeInput.format(cue.endMs)) }
    val startMs = SubtitleTimeInput.parse(start)
    val endMs = SubtitleTimeInput.parse(end)
    val timesValid = startMs != null && endMs != null && endMs > startMs
    val hasNext = track.cues.indexOfFirst { it.id == cue.id } < track.cues.size - 1
    OverlayPanel(
        title = stringResource(R.string.subtitles_cue_title), onDismiss = onDismiss,
        actions = {
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.editor_cancel)) }
            TextButton(
                {
                    if (text != cue.text) actions.onEditText(track.id, cue.id, text)
                    if (timesValid && (startMs != cue.startMs || endMs != cue.endMs)) actions.onRetime(track.id, cue.id, startMs!!, endMs!!)
                    onDismiss()
                },
                Modifier.heightIn(min = 48.dp),
                enabled = timesValid,
            ) { Text(stringResource(R.string.editor_apply)) }
        },
    ) {
        OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.subtitles_cue_text)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, singleLine = true, isError = startMs == null, label = { Text(stringResource(R.string.subtitles_cue_start)) }, modifier = Modifier.weight(1f))
            OutlinedTextField(end, { end = it }, singleLine = true, isError = endMs == null || !timesValid, label = { Text(stringResource(R.string.subtitles_cue_end)) }, modifier = Modifier.weight(1f))
        }
        if (!timesValid) Text(stringResource(R.string.subtitles_time_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextButton({ actions.onSplit(track.id, cue.id); onDismiss() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.subtitles_split)) }
            TextButton({ actions.onMerge(track.id, cue.id); onDismiss() }, Modifier.heightIn(min = 48.dp), enabled = hasNext) { Text(stringResource(R.string.subtitles_merge)) }
            TextButton({ actions.onDuplicate(track.id, cue.id); onDismiss() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.subtitles_duplicate)) }
            TextButton({ actions.onDeleteCue(track.id, cue.id); onDismiss() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}
