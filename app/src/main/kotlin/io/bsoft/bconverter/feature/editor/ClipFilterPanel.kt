package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.FilterGroup
import io.bsoft.bconverter.domain.filter.FilterParam
import io.bsoft.bconverter.domain.filter.FilterPreview
import io.bsoft.bconverter.domain.filter.FilterPresets
import io.bsoft.bconverter.domain.filter.ToneMode
import kotlin.math.roundToInt

/** What the filter panel can do to the selected clip. */
data class ClipFilterActions(
    val onSet: (ClipFilter) -> Unit = {},
    val onApplyToAll: (ClipFilter) -> Unit = {},
)

/**
 * Presets and sliders for the selected clip's look. A slider is applied when released (one undo step per adjustment);
 * choosing a preset, a toggle or Reset applies at once.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ClipFilterPanel(filter: ClipFilter, actions: ClipFilterActions, onDismiss: () -> Unit) {
    var draft by remember(filter) { mutableStateOf(filter) }
    var group by rememberSaveable { mutableStateOf(FilterGroup.BASIC) }
    OverlayPanel(
        title = stringResource(R.string.filter_title), onDismiss = onDismiss,
        actions = {
            TextButton({ actions.onSet(ClipFilter.None) }, Modifier.heightIn(min = 48.dp), enabled = !draft.isIdentity) { Text(stringResource(R.string.filter_reset)) }
            TextButton({ actions.onApplyToAll(draft) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.filter_apply_all)) }
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.filter_done)) }
        },
    ) {
        val presetsLabel = stringResource(R.string.filter_presets)
        Row(Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = presetsLabel }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPresets.all.forEach { p ->
                val selected = if (p.id == "original") draft.isIdentity else draft.presetId == p.id
                FilterChip(selected, { actions.onSet(p.filter) }, { Text(stringResource(FilterLabels.preset(p.id))) }, Modifier.heightIn(min = 48.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterGroup.entries.forEach { g ->
                FilterChip(group == g, { group = g }, { Text(stringResource(FilterLabels.group(g))) }, Modifier.heightIn(min = 48.dp))
            }
        }
        if (group == FilterGroup.DETAIL) {
            // Tone and invert come first: they are the quick switches, the sliders are below.
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToneMode.entries.forEach { t ->
                    FilterChip(draft.tone == t, { actions.onSet(draft.copy(tone = t, presetId = null)) }, { Text(stringResource(FilterLabels.tone(t))) }, Modifier.heightIn(min = 48.dp))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_invert), Modifier.weight(1f))
                Switch(draft.invert, { actions.onSet(draft.copy(invert = it, presetId = null)) })
            }
        }
        for (p in FilterParam.entries.filter { it.group == group }) {
            ParamSlider(p, p.get(draft), onChange = { draft = p.set(draft, it) }, onFinished = { actions.onSet(draft) }, onReset = { actions.onSet(p.set(draft, p.default)) })
        }
        if (FilterPreview.hidesEffects(draft)) {
            Text(stringResource(R.string.filter_preview_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ParamSlider(p: FilterParam, value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit, onReset: () -> Unit) {
    val label = stringResource(FilterLabels.param(p))
    val percent = (value * 100f).roundToInt()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.9f), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value, onChange, Modifier.weight(2f).semantics { contentDescription = label },
            valueRange = p.min..p.max, onValueChangeFinished = onFinished,
        )
        // Tapping the number sets the slider back to its neutral position.
        TextButton(onReset, Modifier.heightIn(min = 48.dp).padding(start = 4.dp), enabled = value != p.default) {
            Text(percent.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}
