package io.bsoft.bconverter.feature.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropEdge
import io.bsoft.bconverter.domain.transform.KenBurns
import io.bsoft.bconverter.domain.transform.KenBurnsMode
import io.bsoft.bconverter.domain.transform.CropEditing
import kotlin.math.roundToInt

/** What the crop panel can do to the selected clip. */
data class ClipTransformActions(
    val onSet: (ClipTransform) -> Unit = {},
    val onApplyToAll: (ClipTransform) -> Unit = {},
    val onKenBurns: (KenBurns) -> Unit = {},
    val onKenBurnsToAll: (KenBurns) -> Unit = {},
)

internal object MotionLabels {
    @StringRes fun mode(m: KenBurnsMode): Int = when (m) {
        KenBurnsMode.NONE -> R.string.motion_none
        KenBurnsMode.ZOOM_IN -> R.string.motion_zoom_in
        KenBurnsMode.ZOOM_OUT -> R.string.motion_zoom_out
        KenBurnsMode.PAN_LEFT -> R.string.motion_pan_left
        KenBurnsMode.PAN_RIGHT -> R.string.motion_pan_right
        KenBurnsMode.PAN_UP -> R.string.motion_pan_up
        KenBurnsMode.PAN_DOWN -> R.string.motion_pan_down
    }
}

internal object CropLabels {
    @StringRes fun shape(s: CropEditing.Shape): Int = when (s) {
        CropEditing.Shape.WHOLE -> R.string.crop_shape_whole
        CropEditing.Shape.SQUARE -> R.string.crop_shape_1_1
        CropEditing.Shape.WIDE -> R.string.crop_shape_16_9
        CropEditing.Shape.TALL -> R.string.crop_shape_9_16
        CropEditing.Shape.CLASSIC -> R.string.crop_shape_4_3
        CropEditing.Shape.PORTRAIT -> R.string.crop_shape_3_4
    }
    @StringRes fun edge(e: CropEdge): Int = when (e) {
        CropEdge.LEFT -> R.string.crop_edge_left
        CropEdge.TOP -> R.string.crop_edge_top
        CropEdge.RIGHT -> R.string.crop_edge_right
        CropEdge.BOTTOM -> R.string.crop_edge_bottom
    }
}

/**
 * Crop shapes, edge sliders, turns and mirrors for the selected clip. The preview shows every change; an edge slider is applied
 * when released (one undo step), a shape or a turn applies at once.
 */
@Composable
fun ClipTransformPanel(transform: ClipTransform, source: Resolution?, actions: ClipTransformActions, onDismiss: () -> Unit, kenBurns: KenBurns = KenBurns.None, showMotion: Boolean = true) {
    var draft by remember(transform) { mutableStateOf(transform) }
    var amount by remember(kenBurns) { mutableStateOf(kenBurns.amount) }
    val w = source?.width ?: 0
    val h = source?.height ?: 0
    OverlayPanel(
        title = stringResource(R.string.crop_title), onDismiss = onDismiss,
        actions = {
            TextButton({ actions.onSet(ClipTransform.None) }, Modifier.heightIn(min = 48.dp), enabled = !draft.isIdentity) { Text(stringResource(R.string.crop_reset)) }
            TextButton({ actions.onApplyToAll(draft) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.crop_apply_all)) }
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.crop_done)) }
        },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A turn changes the wanted shape's direction, so a shape picked earlier is cut again for the new direction.
            fun turned(t: ClipTransform): ClipTransform {
                val shape = CropEditing.shapeOf(draft, w, h)
                return if (shape != null && shape != CropEditing.Shape.WHOLE) t.withCrop(CropEditing.cropFor(shape, t, w, h)) else t
            }
            OutlinedButton({ actions.onSet(turned(draft.rotatedLeft())) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.crop_rotate_left)) }
            OutlinedButton({ actions.onSet(turned(draft.rotatedRight())) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.crop_rotate_right)) }
            FilterChip(draft.flipHorizontal, { actions.onSet(draft.flippedHorizontally()) }, { Text(stringResource(R.string.crop_flip_horizontal)) }, Modifier.heightIn(min = 48.dp))
            FilterChip(draft.flipVertical, { actions.onSet(draft.flippedVertically()) }, { Text(stringResource(R.string.crop_flip_vertical)) }, Modifier.heightIn(min = 48.dp))
        }
        val shapesLabel = stringResource(R.string.crop_shapes)
        val current = CropEditing.shapeOf(draft, w, h)
        Row(Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = shapesLabel }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropEditing.Shape.entries.forEach { s ->
                FilterChip(current == s, { actions.onSet(draft.withCrop(CropEditing.cropFor(s, draft, w, h))) }, { Text(stringResource(CropLabels.shape(s))) }, Modifier.heightIn(min = 48.dp))
            }
        }
        CropEdge.entries.forEach { e ->
            val label = stringResource(CropLabels.edge(e))
            val cut = CropEditing.cut(draft.crop, e)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    cut, { draft = draft.withCrop(CropEditing.withCut(draft.crop, e, it)) }, Modifier.weight(2f).semantics { contentDescription = label },
                    valueRange = 0f..0.9f, onValueChangeFinished = { actions.onSet(draft) },
                )
                TextButton({ actions.onSet(draft.withCrop(CropEditing.withCut(draft.crop, e, 0f))) }, Modifier.heightIn(min = 48.dp).padding(start = 4.dp), enabled = cut != 0f) {
                    Text("${(cut * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (showMotion) {
        Text(stringResource(R.string.motion_title), style = MaterialTheme.typography.titleSmall)
        val motionLabel = stringResource(R.string.motion_title)
        Row(Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = motionLabel }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KenBurnsMode.entries.forEach { m ->
                FilterChip(kenBurns.mode == m, { actions.onKenBurns(KenBurns(m, amount)) }, { Text(stringResource(MotionLabels.mode(m))) }, Modifier.heightIn(min = 48.dp))
            }
        }
        if (!kenBurns.isNone) {
            val strength = stringResource(R.string.motion_strength)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strength, Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    amount, { amount = it }, Modifier.weight(2f).semantics { contentDescription = strength },
                    valueRange = KenBurns.MIN_AMOUNT..KenBurns.MAX_AMOUNT, onValueChangeFinished = { actions.onKenBurns(KenBurns(kenBurns.mode, amount)) },
                )
                Text("${(amount * 100f).roundToInt()}%", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
            TextButton({ actions.onKenBurnsToAll(KenBurns(kenBurns.mode, amount)) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.motion_apply_all)) }
        }
        Text(stringResource(R.string.motion_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
