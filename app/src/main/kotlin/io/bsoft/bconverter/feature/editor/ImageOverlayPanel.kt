package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.overlay.TextOverlay

/**
 * The settings of a picture overlay as numbers: when it shows, where it sits, how big, turned and see-through it is, how it
 * animates, and where it is in the layer order shared with the text. Each change is applied at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageOverlayPanel(overlay: TextOverlay, actions: TextOverlayActions, onDismiss: () -> Unit, playheadMs: Long = 0L) {
    val id = overlay.id
    OverlayPanel(
        title = stringResource(R.string.image_dialog_title), onDismiss = onDismiss,
        actions = {
            TextButton({ actions.onDuplicate(id) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_duplicate)) }
            TextButton({ actions.onDelete(id); onDismiss() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_delete)) }
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_done)) }
        },
    ) {
        Text(stringResource(R.string.image_hint_gestures), style = MaterialTheme.typography.bodySmall)

        Section(R.string.text_section_time)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(R.string.text_start, overlay.startMs, Modifier.weight(1f)) { v -> actions.onRetime(id, v, overlay.endMs) }
            TimeField(R.string.text_end, overlay.endMs, Modifier.weight(1f)) { v -> actions.onRetime(id, overlay.startMs, v) }
        }

        Section(R.string.text_section_position)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PercentField(R.string.text_x, overlay.centerX, 0f, 100f, Modifier.weight(1f)) { actions.onPosition(id, it, overlay.centerY) }
            PercentField(R.string.text_y, overlay.centerY, 0f, 100f, Modifier.weight(1f)) { actions.onPosition(id, overlay.centerX, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(R.string.text_rotation, overlay.rotationDeg, Modifier.weight(1f)) { actions.onRotation(id, it) }
            PercentField(R.string.text_scale, overlay.scale, 10f, 2000f, Modifier.weight(1f)) { actions.onScale(id, it) }
        }
        PercentField(R.string.text_opacity, overlay.style.opacity, 0f, 100f, Modifier.fillMaxWidth()) { v -> actions.onStyle(id) { it.copy(opacity = v) } }

        Section(R.string.text_section_animation)
        AnimationEditor(R.string.text_anim_in, overlay.animIn) { actions.onAnimations(id, it, overlay.animOut) }
        AnimationEditor(R.string.text_anim_out, overlay.animOut) { actions.onAnimations(id, overlay.animIn, it) }

        OverlayKeyframeSection(overlay, playheadMs) { actions.onKeyframes(id, it) }

        Section(R.string.text_section_layer)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ actions.onLayer(id, EditorViewModel.OverlayLayerMove.FRONT) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_layer_front)) }
            OutlinedButton({ actions.onLayer(id, EditorViewModel.OverlayLayerMove.FORWARD) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_layer_forward)) }
            OutlinedButton({ actions.onLayer(id, EditorViewModel.OverlayLayerMove.BACKWARD) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_layer_backward)) }
            OutlinedButton({ actions.onLayer(id, EditorViewModel.OverlayLayerMove.BACK) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_layer_back)) }
        }
    }
}
