package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.canvas.CanvasAspect
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings

/** The colours offered for the bars, with the name each is announced by. */
internal val CanvasSwatches: List<Pair<Int, Int>> = listOf(
    0x000000 to R.string.canvas_black, 0xFFFFFF to R.string.canvas_white, 0x808080 to R.string.canvas_grey,
    0x1E3A8A to R.string.canvas_navy, 0x14532D to R.string.canvas_green, 0x7F1D1D to R.string.canvas_wine,
)

internal fun canvasAspectLabel(aspect: CanvasAspect): Int = when (aspect) {
    CanvasAspect.AUTO -> R.string.canvas_shape_auto
    CanvasAspect.WIDE -> R.string.canvas_wide
    CanvasAspect.TALL -> R.string.canvas_tall
    CanvasAspect.SQUARE -> R.string.canvas_square
    CanvasAspect.PORTRAIT -> R.string.canvas_portrait
}

/** The colour the preview shows in the bars: the chosen colour, or dark grey for a blurred background (the still behind the player covers it once loaded). */
internal fun canvasBarColor(background: CanvasBackground): Color = when (background) {
    is CanvasBackground.Color -> Color(0xFF000000.toInt() or background.rgb)
    CanvasBackground.Blur -> Color(0xFF202020)
}

/** Shape, fit and bar background of the output. Every change is applied at once (one undo step each). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CanvasPanel(settings: CanvasSettings, onChange: (CanvasSettings) -> Unit, onDismiss: () -> Unit) {
    OverlayPanel(
        title = stringResource(R.string.canvas_title), onDismiss = onDismiss,
        actions = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_done)) } },
    ) {
        Text(stringResource(R.string.canvas_shape), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CanvasAspect.entries.forEach { aspect ->
                FilterChip(settings.aspect == aspect, { onChange(settings.copy(aspect = aspect)) }, { Text(stringResource(canvasAspectLabel(aspect))) }, Modifier.heightIn(min = 48.dp))
            }
        }
        Text(stringResource(R.string.canvas_fit_title), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(settings.fit == CanvasFit.FIT, { onChange(settings.copy(fit = CanvasFit.FIT)) }, { Text(stringResource(R.string.canvas_fit_fit)) }, Modifier.heightIn(min = 48.dp))
            FilterChip(settings.fit == CanvasFit.FILL, { onChange(settings.copy(fit = CanvasFit.FILL)) }, { Text(stringResource(R.string.canvas_fit_fill)) }, Modifier.heightIn(min = 48.dp))
        }
        Text(stringResource(R.string.canvas_bars), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CanvasSwatches.forEach { (rgb, name) ->
                val selected = settings.background == CanvasBackground.Color(rgb)
                FilterChip(
                    selected, { onChange(settings.copy(background = CanvasBackground.Color(rgb))) },
                    { Text(stringResource(name)) }, Modifier.heightIn(min = 48.dp),
                    leadingIcon = {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or rgb)))
                    },
                )
            }
            FilterChip(settings.background == CanvasBackground.Blur, { onChange(settings.copy(background = CanvasBackground.Blur)) }, { Text(stringResource(R.string.canvas_blur)) }, Modifier.heightIn(min = 48.dp))
        }
        Text(stringResource(R.string.canvas_bars_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.canvas_preview_note), style = MaterialTheme.typography.bodySmall)
    }
}
