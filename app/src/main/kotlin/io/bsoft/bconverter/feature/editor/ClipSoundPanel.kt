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
import io.bsoft.bconverter.domain.audio.ClipSound
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The level and the two fades of the selected clip's sound; a slider is applied when released (one undo step). */
@Composable
fun ClipSoundPanel(sound: ClipSound, clipDurationMs: Long, onSet: (ClipSound) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(sound) { mutableStateOf(sound) }
    // A fade can be as long as the clip (at most 10 s); the two together are trimmed to the clip when it is exported.
    val maxFade = minOf(ClipSound.MAX_FADE_MS, clipDurationMs).coerceAtLeast(100L).toFloat()
    OverlayPanel(
        title = stringResource(R.string.sound_title), onDismiss = onDismiss,
        actions = {
            TextButton({ onSet(ClipSound.Default) }, Modifier.heightIn(min = 48.dp), enabled = !draft.isDefault) { Text(stringResource(R.string.sound_reset)) }
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.sound_done)) }
        },
    ) {
        SoundSlider(
            stringResource(R.string.sound_volume), stringResource(R.string.editor_volume_value, (draft.volume * 100).roundToInt()),
            draft.volume, 0f..ClipSound.MAX_VOLUME, { draft = draft.copy(volume = it) }, { onSet(draft) },
        )
        SoundSlider(
            stringResource(R.string.sound_fade_in), stringResource(R.string.sound_seconds, draft.fadeInMs / 1000f),
            draft.fadeInMs.toFloat().coerceAtMost(maxFade), 0f..maxFade, { draft = draft.copy(fadeInMs = it.roundToLong().coerceIn(0, ClipSound.MAX_FADE_MS)) }, { onSet(draft) },
        )
        SoundSlider(
            stringResource(R.string.sound_fade_out), stringResource(R.string.sound_seconds, draft.fadeOutMs / 1000f),
            draft.fadeOutMs.toFloat().coerceAtMost(maxFade), 0f..maxFade, { draft = draft.copy(fadeOutMs = it.roundToLong().coerceIn(0, ClipSound.MAX_FADE_MS)) }, { onSet(draft) },
        )
        Text(stringResource(R.string.sound_preview_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SoundSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Slider(value, onChange, Modifier.weight(2f).semantics { contentDescription = label }, valueRange = range, onValueChangeFinished = onFinished)
        Text(valueText, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}
