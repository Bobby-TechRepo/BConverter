package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.subtitle.SubtitleAlignment
import io.bsoft.bconverter.domain.subtitle.SubtitleEditor
import io.bsoft.bconverter.domain.subtitle.SubtitleStyle
import io.bsoft.bconverter.domain.subtitle.SubtitleStylePresets
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import kotlin.math.roundToInt

/** Look of a track: ready-made presets, then size, weight, colours, outline, box, position and margin. Changes apply at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceDialog(track: SubtitleTrack, actions: SubtitleActions, onDismiss: () -> Unit) {
    val style = SubtitleEditor.baseStyle(track)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitles_appearance)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Sample(style)
                Text(stringResource(R.string.appearance_presets), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleStylePresets.all.forEach { p ->
                        FilterChip(selected = style.copy(name = "") == p.style.copy(name = ""), onClick = { actions.onStyle(track.id, p.style) }, label = { Text(stringResource(presetLabel(p.id))) })
                    }
                }
                Labeled(stringResource(R.string.appearance_size, style.fontSize.roundToInt())) {
                    Slider(style.fontSize, { actions.onStyle(track.id, style.copy(fontSize = it.roundToInt().toFloat())) }, valueRange = 16f..120f)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(style.bold, { actions.onStyle(track.id, style.copy(bold = !style.bold)) }, { Text(stringResource(R.string.appearance_bold)) })
                    FilterChip(style.italic, { actions.onStyle(track.id, style.copy(italic = !style.italic)) }, { Text(stringResource(R.string.appearance_italic)) })
                    FilterChip(style.underline, { actions.onStyle(track.id, style.copy(underline = !style.underline)) }, { Text(stringResource(R.string.appearance_underline)) })
                    FilterChip(style.boxed, { actions.onStyle(track.id, style.copy(boxed = !style.boxed)) }, { Text(stringResource(R.string.appearance_box)) })
                }
                Text(stringResource(R.string.appearance_text_color), style = MaterialTheme.typography.labelMedium)
                Swatches(style.primaryColor) { actions.onStyle(track.id, style.copy(primaryColor = it)) }
                Text(stringResource(R.string.appearance_outline_color), style = MaterialTheme.typography.labelMedium)
                Swatches(style.outlineColor) { actions.onStyle(track.id, style.copy(outlineColor = it)) }
                Labeled(stringResource(R.string.appearance_outline_width, style.outlineWidth.roundToInt())) {
                    Slider(style.outlineWidth, { actions.onStyle(track.id, style.copy(outlineWidth = it.roundToInt().toFloat())) }, valueRange = 0f..10f)
                }
                Labeled(stringResource(R.string.appearance_shadow, style.shadow.roundToInt())) {
                    Slider(style.shadow, { actions.onStyle(track.id, style.copy(shadow = it.roundToInt().toFloat())) }, valueRange = 0f..8f)
                }
                Text(stringResource(R.string.appearance_position), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(R.string.appearance_top, style.alignment.isTop, 7), Triple(R.string.appearance_middle, style.alignment.isMiddle, 4), Triple(R.string.appearance_bottom, style.alignment.isBottom, 1),
                    ).forEach { (label, selected, base) ->
                        FilterChip(selected, { actions.onStyle(track.id, style.copy(alignment = aligned(base, style.alignment))) }, { Text(stringResource(label)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(R.string.appearance_left, 0, style.alignment.numpad % 3 == 1), Triple(R.string.appearance_center, 1, style.alignment.numpad % 3 == 2), Triple(R.string.appearance_right, 2, style.alignment.numpad % 3 == 0))
                        .forEach { (label, column, selected) ->
                            FilterChip(selected, { actions.onStyle(track.id, style.copy(alignment = withColumn(style.alignment, column))) }, { Text(stringResource(label)) })
                        }
                }
                Labeled(stringResource(R.string.appearance_margin, style.marginV)) {
                    Slider(style.marginV.toFloat(), { actions.onStyle(track.id, style.copy(marginV = it.roundToInt())) }, valueRange = 0f..200f)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.subtitles_done)) } },
    )
}

/** The numpad row (7 top, 4 middle, 1 bottom) with the column of [current] kept. */
private fun aligned(rowBase: Int, current: SubtitleAlignment): SubtitleAlignment {
    val column = (current.numpad - 1) % 3
    return SubtitleAlignment.fromNumpad(rowBase + column) ?: current
}

private fun withColumn(current: SubtitleAlignment, column: Int): SubtitleAlignment {
    val rowBase = ((current.numpad - 1) / 3) * 3 + 1
    return SubtitleAlignment.fromNumpad(rowBase + column) ?: current
}

private fun presetLabel(id: String): Int = when (id) {
    "classic" -> R.string.preset_classic
    "bold_yellow" -> R.string.preset_bold_yellow
    "boxed" -> R.string.preset_boxed
    "cinematic" -> R.string.preset_cinematic
    "top" -> R.string.preset_top
    else -> R.string.preset_large
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium); content() }
}

/** A rough preview: colour, weight, italics and box are shown; the real size is relative to the video and is not to scale. */
@Composable
private fun Sample(style: SubtitleStyle) {
    Box(Modifier.fillMaxWidth().height(72.dp).clip(MaterialTheme.shapes.small).background(Color(0xFF3A4A5A)), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.appearance_sample),
            color = Color(style.primaryColor),
            fontSize = (style.fontSize / 3f).coerceIn(10f, 32f).sp,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            modifier = if (style.boxed) Modifier.background(Color(style.backColor)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleStylePresets.colors.forEach { color ->
            val isSelected = (color or 0xFF000000.toInt()) == (selected or 0xFF000000.toInt())
            val description = stringResource(R.string.appearance_color_swatch, "#%06X".format(color and 0xFFFFFF)) + if (isSelected) ", " + stringResource(R.string.editor_clip_selected) else ""
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(color))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(color) }.semantics { contentDescription = description },
            )
        }
    }
}
