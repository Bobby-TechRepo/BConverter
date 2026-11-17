package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayBackground
import io.bsoft.bconverter.domain.overlay.OverlayGlow
import io.bsoft.bconverter.domain.overlay.OverlayNumericInput
import io.bsoft.bconverter.domain.overlay.OverlayOutline
import io.bsoft.bconverter.domain.overlay.OverlayShadow
import io.bsoft.bconverter.domain.overlay.OverlayTextAlign
import io.bsoft.bconverter.domain.overlay.TextOverlay
import kotlin.math.abs

/**
 * Every setting of the selected text overlay as numbers and switches, so it can be set exactly (and without touch gestures).
 * Each change is applied at once; invalid input is marked and not applied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextOverlayDialog(overlay: TextOverlay, fonts: OverlayFonts, actions: TextOverlayActions, onDismiss: () -> Unit, playheadMs: Long = 0L) {
    var pickFont by remember { mutableStateOf(false) }
    val id = overlay.id
    val s = overlay.style
    OverlayPanel(
        title = stringResource(R.string.text_dialog_title), onDismiss = onDismiss,
        actions = {
            TextButton({ actions.onDuplicate(id) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_duplicate)) }
            TextButton({ actions.onDelete(id); onDismiss() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_delete)) }
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.text_done)) }
        },
    ) {
                Text(stringResource(R.string.text_hint_gestures), style = MaterialTheme.typography.bodySmall)
                var text by remember(id) { mutableStateOf(overlay.text) }
                LaunchedEffect(overlay.text) { if (overlay.text != text) text = overlay.text }
                OutlinedTextField(text, { text = it; actions.onText(id, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.text_field_text)) }, minLines = 1, maxLines = 4)

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PercentField(R.string.text_size, s.sizeFraction, 1f, 100f, Modifier.weight(1f)) { v -> actions.onStyle(id) { it.copy(sizeFraction = v) } }
                    PercentField(R.string.text_wrap, overlay.widthFraction ?: 0f, 0f, 100f, Modifier.weight(1f)) { v -> actions.onWidth(id, v.takeIf { it > 0f }) }
                }

                Section(R.string.text_section_font)
                OutlinedButton({ pickFont = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s.fontFamily ?: stringResource(R.string.text_font_default)) }
                if (!fonts.isExact(s)) Text(stringResource(R.string.text_font_inexact), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 200, 300, 400, 500, 600, 700, 800, 900).forEach { w ->
                        FilterChip(s.fontWeight == w, { actions.onStyle(id) { it.copy(fontWeight = w) } }, { Text("$w") }, Modifier.heightIn(min = 48.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_italic), Modifier.weight(1f)); Switch(s.italic, { v -> actions.onStyle(id) { it.copy(italic = v) } })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_underline), Modifier.weight(1f)); Switch(s.underline, { v -> actions.onStyle(id) { it.copy(underline = v) } })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextAlign.entries.forEach { a ->
                        FilterChip(
                            s.align == a, { actions.onStyle(id) { it.copy(align = a) } },
                            { Text(stringResource(when (a) { OverlayTextAlign.LEFT -> R.string.text_align_left; OverlayTextAlign.CENTER -> R.string.text_align_center; OverlayTextAlign.RIGHT -> R.string.text_align_right })) },
                            Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(R.string.text_letter_spacing, s.letterSpacing, Modifier.weight(1f), min = -0.5f, max = 5f) { v -> actions.onStyle(id) { it.copy(letterSpacing = v) } }
                    NumberField(R.string.text_line_spacing, s.lineSpacing, Modifier.weight(1f), min = 0.1f, max = 5f) { v -> actions.onStyle(id) { it.copy(lineSpacing = v) } }
                }

                Section(R.string.text_section_color)
                Swatches(s.color) { c -> actions.onStyle(id) { it.copy(color = c) } }
                PercentField(R.string.text_opacity, s.opacity, 0f, 100f, Modifier.fillMaxWidth()) { v -> actions.onStyle(id) { it.copy(opacity = v) } }

                Section(R.string.text_section_effects)
                EffectRow(R.string.text_outline, s.outline != null, { on -> actions.onStyle(id) { it.copy(outline = if (on) OverlayOutline() else null) } }) {
                    s.outline?.let { o ->
                        Swatches(o.color) { c -> actions.onStyle(id) { it.copy(outline = o.copy(color = c)) } }
                        PercentField(R.string.text_outline_width, o.widthFraction, 0f, 50f, Modifier.fillMaxWidth()) { v -> actions.onStyle(id) { it.copy(outline = o.copy(widthFraction = v)) } }
                    }
                }
                EffectRow(R.string.text_shadow, s.shadow != null, { on -> actions.onStyle(id) { it.copy(shadow = if (on) OverlayShadow() else null) } }) {
                    s.shadow?.let { sh ->
                        Swatches(sh.color) { c -> actions.onStyle(id) { it.copy(shadow = sh.copy(color = c)) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PercentField(R.string.text_shadow_x, sh.dx, -200f, 200f, Modifier.weight(1f)) { v -> actions.onStyle(id) { it.copy(shadow = sh.copy(dx = v)) } }
                            PercentField(R.string.text_shadow_y, sh.dy, -200f, 200f, Modifier.weight(1f)) { v -> actions.onStyle(id) { it.copy(shadow = sh.copy(dy = v)) } }
                        }
                        PercentField(R.string.text_shadow_blur, sh.blur, 0f, 200f, Modifier.fillMaxWidth()) { v -> actions.onStyle(id) { it.copy(shadow = sh.copy(blur = v)) } }
                    }
                }
                EffectRow(R.string.text_glow, s.glow != null, { on -> actions.onStyle(id) { it.copy(glow = if (on) OverlayGlow() else null) } }) {
                    s.glow?.let { g ->
                        Swatches(g.color) { c -> actions.onStyle(id) { it.copy(glow = g.copy(color = c)) } }
                        PercentField(R.string.text_glow_radius, g.radius, 0f, 200f, Modifier.fillMaxWidth()) { v -> actions.onStyle(id) { it.copy(glow = g.copy(radius = v)) } }
                    }
                }
                EffectRow(R.string.text_background, s.background != null, { on -> actions.onStyle(id) { it.copy(background = if (on) OverlayBackground() else null) } }) {
                    s.background?.let { b ->
                        Swatches(b.color) { c -> actions.onStyle(id) { it.copy(background = b.copy(color = c)) } }
                        PercentField(R.string.text_background_padding, b.padding, 0f, 200f, Modifier.fillMaxWidth()) { v -> actions.onStyle(id) { it.copy(background = b.copy(padding = v)) } }
                    }
                }

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
    if (pickFont) FontPickerDialog(s.fontFamily, fonts, onPick = { family ->
        actions.onStyle(id) { it.copy(fontFamily = family) }
        family?.let(fonts::markUsed)
        pickFont = false
    }, onDismiss = { pickFont = false })
}

@Composable
internal fun Section(titleRes: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(stringResource(titleRes), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun EffectRow(labelRes: Int, enabled: Boolean, onToggle: (Boolean) -> Unit, details: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f)); Switch(enabled, onToggle)
    }
    if (enabled) Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
}

/** A field for a fraction shown as a percentage. A value outside [minPercent]..[maxPercent] is marked and not applied. */
@Composable
internal fun PercentField(labelRes: Int, value: Float, minPercent: Float, maxPercent: Float, modifier: Modifier, onValue: (Float) -> Unit) {
    var text by remember { mutableStateOf(OverlayNumericInput.percentText(value)) }
    val parsed = OverlayNumericInput.percent(text, minPercent, maxPercent)
    LaunchedEffect(value) { if (parsed == null || abs(parsed - value) > 0.0005f) text = OverlayNumericInput.percentText(value) }
    OutlinedTextField(
        text, { text = it; OverlayNumericInput.percent(it, minPercent, maxPercent)?.let(onValue) }, modifier,
        label = { Text(stringResource(labelRes)) }, singleLine = true, isError = parsed == null,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        supportingText = { if (parsed == null) Text(stringResource(R.string.text_value_invalid)) },
    )
}

/** A field for a plain number (degrees, em, multiplier). */
@Composable
internal fun NumberField(labelRes: Int, value: Float, modifier: Modifier, min: Float = -100_000f, max: Float = 100_000f, onValue: (Float) -> Unit) {
    var text by remember { mutableStateOf(OverlayNumericInput.numberText(value)) }
    val parsed = OverlayNumericInput.degrees(text)?.takeIf { it in min..max }
    LaunchedEffect(value) { if (parsed == null || abs(parsed - value) > 0.005f) text = OverlayNumericInput.numberText(value) }
    OutlinedTextField(
        text, { text = it; OverlayNumericInput.degrees(it)?.takeIf { v -> v in min..max }?.let(onValue) }, modifier,
        label = { Text(stringResource(labelRes)) }, singleLine = true, isError = parsed == null,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        supportingText = { if (parsed == null) Text(stringResource(R.string.text_value_invalid)) },
    )
}

@Composable
internal fun TimeField(labelRes: Int, ms: Long, modifier: Modifier, onValue: (Long) -> Unit) {
    var text by remember { mutableStateOf(SubtitleTimeInput.format(ms)) }
    val parsed = SubtitleTimeInput.parse(text)
    LaunchedEffect(ms) { if (parsed != ms) text = SubtitleTimeInput.format(ms) }
    OutlinedTextField(
        text, { text = it; SubtitleTimeInput.parse(it)?.let(onValue) }, modifier,
        label = { Text(stringResource(labelRes)) }, singleLine = true, isError = parsed == null,
        supportingText = { if (parsed == null) Text(stringResource(R.string.subtitles_time_invalid)) },
    )
}

private val animationKinds = listOf(
    AnimationKind.FADE to R.string.text_anim_fade, AnimationKind.SLIDE_LEFT to R.string.text_anim_slide_left, AnimationKind.SLIDE_RIGHT to R.string.text_anim_slide_right,
    AnimationKind.SLIDE_UP to R.string.text_anim_slide_up, AnimationKind.SLIDE_DOWN to R.string.text_anim_slide_down, AnimationKind.SCALE to R.string.text_anim_scale,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimationEditor(titleRes: Int, animation: OverlayAnimation?, onChange: (OverlayAnimation?) -> Unit) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(animation == null, { onChange(null) }, { Text(stringResource(R.string.text_anim_none)) }, Modifier.heightIn(min = 48.dp))
        animationKinds.forEach { (kind, label) ->
            FilterChip(animation?.kind == kind, { onChange(OverlayAnimation(kind, animation?.durationMs ?: 500)) }, { Text(stringResource(label)) }, Modifier.heightIn(min = 48.dp))
        }
    }
    if (animation != null) {
        var text by remember { mutableStateOf(animation.durationMs.toString()) }
        val parsed = text.toLongOrNull()?.takeIf { it in 50..10_000 }
        OutlinedTextField(
            text, { text = it; it.toLongOrNull()?.takeIf { v -> v in 50..10_000 }?.let { v -> onChange(animation.copy(durationMs = v)) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.text_anim_duration)) }, singleLine = true, isError = parsed == null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            supportingText = { if (parsed == null) Text(stringResource(R.string.text_value_invalid)) },
        )
    }
}

/** Installed fonts, recently used first. Each name is drawn in its own typeface (rows are created lazily, faces are cached). */
@Composable
private fun FontPickerDialog(current: String?, fonts: OverlayFonts, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val families = remember { fonts.families() }
    val recents = remember { fonts.recents() }
    var query by remember { mutableStateOf("") }
    val shown = remember(query) {
        val q = query.trim().lowercase()
        families.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_pick_font)) },
        confirmButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.editor_cancel)) } },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.fonts_search_hint)) })
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { FontRow(stringResource(R.string.text_font_default), null, current == null) { onPick(null) } }
                    if (query.isBlank()) items(recents) { r -> FontRow("★ $r", fonts.previewFace(r), current == r) { onPick(r) } }
                    items(shown, key = { it.name }) { f -> FontRow(f.name, fonts.previewFace(f.name), current == f.name) { onPick(f.name) } }
                }
            }
        },
    )
}

@Composable
private fun FontRow(name: String, face: androidx.compose.ui.text.font.FontFamily?, selected: Boolean, onClick: () -> Unit) {
    Text(
        name + if (selected) " ✓" else "",
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge.let { if (face != null) it.copy(fontFamily = face) else it },
    )
}
