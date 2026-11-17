package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.timeline.ChromaKey
import io.bsoft.bconverter.domain.timeline.ChromaKeyMath
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipKeyframe
import io.bsoft.bconverter.domain.timeline.PipKeyframes
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.domain.timeline.PipMotion
import io.bsoft.bconverter.domain.timeline.PipShape
import kotlin.math.roundToInt

/** What the layout panel can ask for; each slider is applied when released (one undo step). */
data class PipActions(
    val onLayout: (PipLayout) -> Unit = {},
    val onVolume: (Float) -> Unit = {},
    val onChromaKey: (ChromaKey?) -> Unit = {},
    val onFades: (fadeInMs: Long, fadeOutMs: Long) -> Unit = { _, _ -> },
    val onFront: () -> Unit = {},
    val onMotion: (PipMotion?) -> Unit = {},
    val onSpeed: (Float) -> Unit = {},
    val onShape: (PipShape) -> Unit = {},
    val onKeyframes: (List<PipKeyframe>) -> Unit = {},
)

/** Size, position, opacity and volume of the selected overlay video, with the same controls the preview gestures have. */
@Composable
fun PipPanel(clip: PipClip, actions: PipActions, frame: ImageBitmap? = null, playheadOffsetMs: Long = 0L, onDismiss: () -> Unit) {
    val layout = clip.layout
    OverlayPanel(
        title = stringResource(R.string.pip_title), onDismiss = onDismiss,
        actions = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pip_done)) } },
    ) {
        val keyed = clip.keyframes.isNotEmpty()
        if (!keyed) {
        PipSlider(R.string.pip_size, layout.width, PipLayout.MIN_WIDTH..PipLayout.MAX_WIDTH) { actions.onLayout(layout.copy(width = it)) }
        PipSlider(R.string.pip_horizontal, layout.centerX, 0f..1f) { actions.onLayout(layout.copy(centerX = it)) }
        PipSlider(R.string.pip_vertical, layout.centerY, 0f..1f) { actions.onLayout(layout.copy(centerY = it)) }
        PipSlider(R.string.pip_opacity, layout.opacity, PipLayout.MIN_OPACITY..1f) { actions.onLayout(layout.copy(opacity = it)) }
        }
        KeyframeSection(clip, playheadOffsetMs, actions.onKeyframes)
        Text(stringResource(R.string.pip_shape), style = MaterialTheme.typography.labelLarge)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(PipShape.RECTANGLE to R.string.pip_shape_rectangle, PipShape.ROUNDED to R.string.pip_shape_rounded, PipShape.CIRCLE to R.string.pip_shape_circle).forEach { (s, name) ->
                FilterChip(clip.shape == s, { actions.onShape(s) }, { Text(stringResource(name)) }, Modifier.heightIn(min = 48.dp))
            }
        }
        // A frame is drawn around a rectangle only; a rounded or circular picture has none.
        if (clip.shape == PipShape.RECTANGLE && clip.motion?.endWidth == null) PipSlider(R.string.pip_border, layout.borderWidth, 0f..PipLayout.MAX_BORDER) { actions.onLayout(layout.copy(borderWidth = it)) }
        if (clip.shape == PipShape.RECTANGLE && clip.motion?.endWidth == null && layout.borderWidth > 0f) {
            Text(stringResource(R.string.pip_border_color), style = MaterialTheme.typography.labelLarge)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                CanvasSwatches.forEach { (rgb, name) ->
                    FilterChip(layout.borderRgb == rgb, { actions.onLayout(layout.copy(borderRgb = rgb)) }, { Text(stringResource(name)) }, Modifier.heightIn(min = 48.dp))
                }
            }
        }
        val maxFadeMs = minOf(10_000L, clip.durationMs / 2).coerceAtLeast(100L)
        PipFadeSlider(R.string.pip_fade_in, clip.fadeInMs, maxFadeMs) { actions.onFades(it, clip.fadeOutMs) }
        PipFadeSlider(R.string.pip_fade_out, clip.fadeOutMs, maxFadeMs) { actions.onFades(clip.fadeInMs, it) }
        PipSlider(R.string.pip_volume, clip.volume, 0f..2f, actions.onVolume)
        Text(stringResource(R.string.pip_speed), style = MaterialTheme.typography.labelLarge)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            PIP_SPEEDS.forEach { s ->
                val label = stringResource(R.string.pip_speed_value, if (s % 1f == 0f) s.toInt().toString() else s.toString())
                FilterChip(clip.speed == s, { actions.onSpeed(s) }, { Text(label) }, Modifier.heightIn(min = 48.dp))
            }
        }
        val motion = clip.motion
        if (!keyed) {
        val glideText = stringResource(R.string.pip_glide)
        val easeText = stringResource(R.string.pip_glide_ease)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pip_glide), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(motion != null, { on -> actions.onMotion(if (on) PipMotion(endX = (layout.centerX + 0.3f).let { if (it > 1f) layout.centerX - 0.3f else it }.coerceIn(0f, 1f), endY = layout.centerY) else null) }, Modifier.semantics { contentDescription = glideText })
        }
        if (motion != null) {
            PipSlider(R.string.pip_glide_x, motion.endX, 0f..1f) { actions.onMotion(motion.copy(endX = it)) }
            PipSlider(R.string.pip_glide_y, motion.endY, 0f..1f) { actions.onMotion(motion.copy(endY = it)) }
            PipSlider(R.string.pip_glide_size, motion.endWidth ?: layout.width, PipLayout.MIN_WIDTH..PipLayout.MAX_WIDTH) {
                actions.onMotion(motion.copy(endWidth = if (kotlin.math.abs(it - layout.width) < 0.01f) null else it))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pip_glide_ease), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(motion.eased, { actions.onMotion(motion.copy(eased = it)) }, Modifier.semantics { contentDescription = easeText })
            }
        }
        }
        ChromaKeySection(clip.chromaKey, frame, actions.onChromaKey)
        OutlinedButton(actions.onFront, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.pip_front)) }
        Text(stringResource(R.string.pip_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.pip_preview_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PipSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onSet: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    val text = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Slider(
            draft, { draft = it }, Modifier.weight(2f).semantics { contentDescription = text },
            valueRange = range, onValueChangeFinished = { onSet(draft.coerceIn(range)) },
        )
        Text(stringResource(R.string.pip_percent, (draft * 100).roundToInt()), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** A fade length in seconds, from 0 to [maxMs]; applied when released. */
@Composable
private fun PipFadeSlider(label: Int, valueMs: Long, maxMs: Long, onSet: (Long) -> Unit) {
    var draft by remember(valueMs) { mutableFloatStateOf(valueMs / 1000f) }
    val text = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Slider(
            draft, { draft = it }, Modifier.weight(2f).semantics { contentDescription = text },
            valueRange = 0f..(maxMs / 1000f), onValueChangeFinished = { onSet((draft * 1000f).roundToInt().toLong().coerceIn(0L, maxMs)) },
        )
        Text(stringResource(R.string.pip_seconds, draft), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Switch and controls for removing one colour; the still frame shows the result on a chequered background and a tap on it picks the colour. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChromaKeySection(key: ChromaKey?, frame: ImageBitmap?, onKey: (ChromaKey?) -> Unit) {
    val title = stringResource(R.string.pip_key)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(key != null, { on -> onKey(if (on) ChromaKey() else null) }, Modifier.semantics { contentDescription = title })
    }
    if (key == null) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        listOf(0x00FF00 to R.string.pip_key_green, 0x0000FF to R.string.pip_key_blue).forEach { (rgb, name) ->
            FilterChip(key.rgb == rgb, { onKey(key.copy(rgb = rgb)) }, { Text(stringResource(name)) }, Modifier.heightIn(min = 48.dp))
        }
    }
    PipSlider(R.string.pip_key_range, key.similarity, ChromaKey.MIN_SIMILARITY..ChromaKey.MAX_SIMILARITY) { onKey(key.copy(similarity = it)) }
    PipSlider(R.string.pip_key_edge, key.blend, 0f..ChromaKey.MAX_BLEND) { onKey(key.copy(blend = it)) }
    if (frame != null) KeyedFrame(frame, key, onKey)
    Text(stringResource(R.string.pip_key_hint), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.pip_key_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun KeyedFrame(frame: ImageBitmap, key: ChromaKey, onKey: (ChromaKey?) -> Unit) {
    val source = remember(frame) { frame.asAndroidBitmap().let { b -> b.copy(android.graphics.Bitmap.Config.ARGB_8888, false) } }
    val keyed = remember(source, key) {
        val w = source.width; val h = source.height
        val px = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        android.graphics.Bitmap.createBitmap(ChromaKeyMath.apply(px, key), w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val description = stringResource(R.string.pip_key_frame_description)
    val light = androidx.compose.ui.graphics.Color(0xFFCCCCCC); val dark = androidx.compose.ui.graphics.Color(0xFF888888)
    Box(
        Modifier.fillMaxWidth().aspectRatio(source.width.toFloat() / source.height).semantics { contentDescription = description }
            .pointerInput(source, key) {
                detectTapGestures { at ->
                    val x = (at.x / size.width * source.width).toInt().coerceIn(0, source.width - 1)
                    val y = (at.y / size.height * source.height).toInt().coerceIn(0, source.height - 1)
                    onKey(key.copy(rgb = source.getPixel(x, y) and 0xFFFFFF))
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val cell = 12.dp.toPx()
            var row = 0
            var y = 0f
            while (y < size.height) {
                var col = 0
                var x = 0f
                while (x < size.width) {
                    drawRect(if ((row + col) % 2 == 0) light else dark, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                    x += cell; col++
                }
                y += cell; row++
            }
        }
        androidx.compose.foundation.Image(keyed, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
    }
}

/** The speeds offered for an overlay clip. */
internal val PIP_SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)

/** The animation of an overlay: keyframes added at the playhead, each with its own position, size and opacity; in between it moves smoothly. */
@Composable
private fun KeyframeSection(clip: PipClip, playheadOffsetMs: Long, onKeyframes: (List<PipKeyframe>) -> Unit) {
    val keys = clip.keyframes
    var selected by remember(clip.id) { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, (keys.size - 1).coerceAtLeast(0))
    Text(stringResource(R.string.pip_keyframes), style = MaterialTheme.typography.labelLarge)
    if (keys.isEmpty()) {
        Text(stringResource(R.string.pip_keyframes_hint), style = MaterialTheme.typography.bodySmall)
    } else {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            keys.forEachIndexed { i, k ->
                val label = stringResource(R.string.pip_keyframe_time, "%.1f".format(k.offsetMs / 1000.0))
                FilterChip(i == index, { selected = i }, { Text(label) }, Modifier.heightIn(min = 48.dp))
            }
        }
        val k = keys[index]
        PipSlider(R.string.pip_horizontal, k.x, 0f..1f) { onKeyframes(PipKeyframes.replace(keys, index, k.copy(x = it))) }
        PipSlider(R.string.pip_vertical, k.y, 0f..1f) { onKeyframes(PipKeyframes.replace(keys, index, k.copy(y = it))) }
        PipSlider(R.string.pip_size, k.width, PipLayout.MIN_WIDTH..PipLayout.MAX_WIDTH) { onKeyframes(PipKeyframes.replace(keys, index, k.copy(width = it))) }
        PipSlider(R.string.pip_opacity, k.opacity, 0f..1f) { onKeyframes(PipKeyframes.replace(keys, index, k.copy(opacity = it))) }
        if (index < keys.lastIndex) KeyframeEaseRow(k.eased) { onKeyframes(PipKeyframes.replace(keys, index, k.copy(eased = it))) }
    }
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            {
                val added = PipKeyframes.addAt(clip, playheadOffsetMs)
                if (added != keys) { onKeyframes(added); selected = added.indexOfFirst { it.offsetMs == playheadOffsetMs }.coerceAtLeast(0) }
            },
            Modifier.heightIn(min = 48.dp), enabled = playheadOffsetMs in 0..clip.durationMs && keys.size < PipKeyframes.MAX,
        ) { Text(stringResource(R.string.pip_keyframe_add)) }
        if (keys.isNotEmpty()) {
            OutlinedButton({ onKeyframes(PipKeyframes.remove(keys, index)) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pip_keyframe_delete)) }
            TextButton({ onKeyframes(emptyList()) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pip_keyframes_clear)) }
        }
    }
}

/** Whether the change from the chosen keyframe to the next starts and ends slowly instead of at a steady pace. */
@Composable
internal fun KeyframeEaseRow(eased: Boolean, onSet: (Boolean) -> Unit) {
    val text = stringResource(R.string.pip_keyframe_ease)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(eased, onSet, Modifier.semantics { contentDescription = text })
    }
}
