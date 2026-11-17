package io.bsoft.bconverter.feature.preview

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.feature.converter.formatDuration
import io.bsoft.bconverter.media.player.PlaybackError
import io.bsoft.bconverter.media.player.PlaybackState
import io.bsoft.bconverter.media.player.PlaybackStatus

/** Stateless transport controls: a round play/pause button, a seek slider, elapsed / total time, and a plain-language error. */
@Composable
fun PreviewControls(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val usable = state.status == PlaybackStatus.Ready || state.status == PlaybackStatus.Ended
    val seekLabel = stringResource(R.string.preview_seek)
    val brand = BConverterTheme.brand
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        state.error?.let {
            Text(
                stringResource(it.messageRes()),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlayPause,
                enabled = usable,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.onSurface.copy(alpha = 0.38f)),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .then(if (usable) Modifier.background(brand.signature) else Modifier.background(colors.surfaceContainerHighest)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(if (state.isPlaying) R.string.preview_pause else R.string.preview_play),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Slider(
                value = state.progress,
                onValueChange = { onSeek((it * state.durationMs).toLong()) },
                enabled = usable,
                colors = SliderDefaults.colors(
                    thumbColor = colors.onSurface,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceContainerHighest,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = seekLabel },
            )
            Text(
                stringResource(R.string.preview_time, formatDuration(state.positionMs), formatDuration(state.durationMs)),
                // Tabular figures keep the time from jittering while it counts.
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun PlaybackError.messageRes(): Int = when (this) {
    PlaybackError.SourceNotFound -> R.string.preview_error_open
    PlaybackError.Unsupported, PlaybackError.Decoder -> R.string.preview_error_unsupported
    PlaybackError.Unknown -> R.string.preview_error_generic
}
