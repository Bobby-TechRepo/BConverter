package io.bsoft.bconverter.feature.preview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.feature.converter.formatDuration
import io.bsoft.bconverter.media.player.PlaybackError
import io.bsoft.bconverter.media.player.PlaybackState
import io.bsoft.bconverter.media.player.PlaybackStatus

/** Stateless transport controls: play/pause, seek slider, elapsed / total time, and a plain-language error. */
@Composable
fun PreviewControls(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val usable = state.status == PlaybackStatus.Ready || state.status == PlaybackStatus.Ended
    val seekLabel = stringResource(R.string.preview_seek)
    Column(modifier) {
        state.error?.let {
            Text(
                stringResource(it.messageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause, enabled = usable) {
                Icon(
                    imageVector = if (state.isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.preview_pause else R.string.preview_play),
                )
            }
            Slider(
                value = state.progress,
                onValueChange = { onSeek((it * state.durationMs).toLong()) },
                enabled = usable,
                modifier = Modifier.weight(1f).semantics { contentDescription = seekLabel },
            )
            Text(
                stringResource(R.string.preview_time, formatDuration(state.positionMs), formatDuration(state.durationMs)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
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

/** Material's pause glyph (not part of the core icon set, so drawn here). */
private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
        }
    }.build()
}
