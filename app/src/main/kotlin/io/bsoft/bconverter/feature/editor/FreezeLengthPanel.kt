package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.timeline.FreezeFrame
import kotlin.math.roundToLong

/** How long the selected freeze frame is held; the slider is applied when released (one undo step). */
@Composable
fun FreezeLengthPanel(lengthMs: Long, onSet: (Long) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(lengthMs) { mutableFloatStateOf(lengthMs.toFloat()) }
    val label = stringResource(R.string.editor_freeze_length)
    OverlayPanel(
        title = stringResource(R.string.freeze_length_title), onDismiss = onDismiss,
        actions = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.freeze_done)) } },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Slider(
                draft, { draft = it }, Modifier.weight(2f).semantics { contentDescription = label },
                valueRange = FreezeFrame.MIN_MS.toFloat()..FreezeFrame.MAX_MS.toFloat(),
                onValueChangeFinished = { onSet(draft.roundToLong().coerceIn(FreezeFrame.MIN_MS, FreezeFrame.MAX_MS)) },
            )
            Text(stringResource(R.string.freeze_length_value, draft / 1000f), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.freeze_preview_note), style = MaterialTheme.typography.bodySmall)
    }
}
