package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.overlay.OverlayKeyframe
import io.bsoft.bconverter.domain.overlay.OverlayKeyframes
import io.bsoft.bconverter.domain.overlay.TextOverlay

/**
 * The keyframe animation of a text or picture overlay: keyframes added at the playhead, each with its own position, zoom and
 * opacity; in between the overlay changes smoothly. [playheadMs] is on the project timeline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayKeyframeSection(overlay: TextOverlay, playheadMs: Long, onKeyframes: (List<OverlayKeyframe>) -> Unit) {
    val keys = overlay.keyframes
    val offset = playheadMs - overlay.startMs
    var selected by remember(overlay.id) { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, (keys.size - 1).coerceAtLeast(0))
    Text(stringResource(R.string.pip_keyframes), style = MaterialTheme.typography.labelLarge)
    if (keys.isEmpty()) {
        Text(stringResource(R.string.overlay_keyframes_hint), style = MaterialTheme.typography.bodySmall)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEachIndexed { i, k ->
                val label = stringResource(R.string.pip_keyframe_time, "%.1f".format(k.offsetMs / 1000.0))
                FilterChip(i == index, { selected = i }, { Text(label) }, Modifier.heightIn(min = 48.dp))
            }
        }
        val k = keys[index]
        PipSlider(R.string.pip_horizontal, k.x, 0f..1f) { onKeyframes(OverlayKeyframes.replace(keys, index, k.copy(x = it))) }
        PipSlider(R.string.pip_vertical, k.y, 0f..1f) { onKeyframes(OverlayKeyframes.replace(keys, index, k.copy(y = it))) }
        PipSlider(R.string.overlay_keyframe_zoom, k.zoom, OverlayKeyframes.MIN_ZOOM..OverlayKeyframes.MAX_ZOOM) { onKeyframes(OverlayKeyframes.replace(keys, index, k.copy(zoom = it))) }
        TurnSlider(k.rotationDeg) { onKeyframes(OverlayKeyframes.replace(keys, index, k.copy(rotationDeg = it))) }
        PipSlider(R.string.pip_opacity, k.opacity, 0f..1f) { onKeyframes(OverlayKeyframes.replace(keys, index, k.copy(opacity = it))) }
        if (index < keys.lastIndex) KeyframeEaseRow(k.eased) { onKeyframes(OverlayKeyframes.replace(keys, index, k.copy(eased = it))) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            {
                val added = OverlayKeyframes.addAt(overlay, offset)
                if (added != keys) { onKeyframes(added); selected = added.indexOfFirst { it.offsetMs == offset }.coerceAtLeast(0) }
            },
            Modifier.heightIn(min = 48.dp), enabled = offset in 0..overlay.durationMs && keys.size < OverlayKeyframes.MAX,
        ) { Text(stringResource(R.string.pip_keyframe_add)) }
        if (keys.isNotEmpty()) {
            OutlinedButton({ onKeyframes(OverlayKeyframes.remove(keys, index)) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pip_keyframe_delete)) }
            TextButton({ onKeyframes(emptyList()) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pip_keyframes_clear)) }
        }
    }
}

/** The extra turn of a keyframe in degrees (clockwise is positive), as a slider with its value written out. */
@Composable
private fun TurnSlider(degrees: Float, onSet: (Float) -> Unit) {
    var draft by remember(degrees) { mutableFloatStateOf(degrees) }
    val text = stringResource(R.string.overlay_keyframe_turn)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Slider(
            draft, { draft = it }, Modifier.weight(2f).semantics { contentDescription = text },
            valueRange = -OverlayKeyframes.MAX_TURN_DEG..OverlayKeyframes.MAX_TURN_DEG, onValueChangeFinished = { onSet(draft.roundToInt().toFloat()) },
        )
        Text(stringResource(R.string.overlay_keyframe_turn_value, draft.roundToInt()), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}
