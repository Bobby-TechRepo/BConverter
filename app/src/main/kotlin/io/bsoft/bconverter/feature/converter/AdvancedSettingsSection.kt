package io.bsoft.bconverter.feature.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.usecase.AdvancedChoices
import io.bsoft.bconverter.domain.usecase.AdvancedOptions
import kotlin.math.roundToInt

private enum class QualityMode { PRESET, CRF, BITRATE }

private const val DEFAULT_CRF = 23
private const val DEFAULT_BITRATE_KBPS = 2000

/**
 * The advanced conversion settings (spec §18), collapsed by default. Every control starts at "preset default" and only
 * offers values the current codec and source allow; [choices] decides what is shown at all.
 */
@Composable
internal fun AdvancedSettingsSection(choices: AdvancedChoices, options: AdvancedOptions, onChange: (AdvancedOptions) -> Unit) {
    if (!choices.videoEncoded && !choices.audioEncoded) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.advanced_title)
    val state = stringResource(if (expanded) R.string.advanced_expanded else R.string.advanced_collapsed)
    Surface(
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, Modifier.weight(1f).semantics { heading(); stateDescription = state },
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.advanced_hide else R.string.advanced_show))
            }
        }
        if (expanded) {
            if (choices.videoEncoded) VideoControls(choices, options, onChange)
            if (choices.audioEncoded) AudioControls(choices, options, onChange)
        }
    }
    }
}

@Composable
private fun VideoControls(choices: AdvancedChoices, options: AdvancedOptions, onChange: (AdvancedOptions) -> Unit) {
    val presetDefault = stringResource(R.string.advanced_preset_default)
    Text(stringResource(R.string.advanced_video), style = MaterialTheme.typography.titleSmall)
    QualityControl(choices, options, onChange)
    if (choices.resolutionHeights.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_resolution),
            selectedText = options.resolutionHeight?.let { "${it}p" } ?: presetDefault,
            options = listOf<Pair<String, Int?>>(presetDefault to null) + choices.resolutionHeights.map { "${it}p" to it },
            onSelect = { onChange(options.copy(resolutionHeight = it)) },
        )
    }
    if (choices.frameRates.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_frame_rate),
            selectedText = options.frameRate?.let { stringResource(R.string.advanced_fps, formatFps(it)) } ?: presetDefault,
            options = listOf<Pair<String, Double?>>(presetDefault to null) + choices.frameRates.map { stringResource(R.string.advanced_fps, formatFps(it)) to it },
            onSelect = { onChange(options.copy(frameRate = it)) },
        )
    }
    if (choices.profiles.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_profile), selectedText = options.profile ?: presetDefault,
            options = listOf<Pair<String, String?>>(presetDefault to null) + choices.profiles.map { it to it },
            onSelect = { onChange(options.copy(profile = it)) },
        )
    }
    if (choices.levels.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_level), selectedText = options.level ?: presetDefault,
            options = listOf<Pair<String, String?>>(presetDefault to null) + choices.levels.map { it to it },
            onSelect = { onChange(options.copy(level = it)) },
        )
    }
    if (choices.pixelFormats.size > 1) {
        Dropdown(
            label = stringResource(R.string.advanced_pixel_format), selectedText = options.pixelFormat ?: presetDefault,
            options = listOf<Pair<String, String?>>(presetDefault to null) + choices.pixelFormats.map { it to it },
            onSelect = { onChange(options.copy(pixelFormat = it)) },
        )
    }
}

@Composable
private fun QualityControl(choices: AdvancedChoices, options: AdvancedOptions, onChange: (AdvancedOptions) -> Unit) {
    val presetDefault = stringResource(R.string.advanced_preset_default)
    val crfLabel = stringResource(R.string.advanced_quality_crf)
    val bitrateLabel = stringResource(R.string.advanced_quality_bitrate)
    val mode = when (options.quality) { is VideoQuality.Crf -> QualityMode.CRF; is VideoQuality.Bitrate -> QualityMode.BITRATE; null -> QualityMode.PRESET }
    val modes = buildList {
        add(presetDefault to QualityMode.PRESET)
        if (choices.crfRange != null) add(crfLabel to QualityMode.CRF)
        add(bitrateLabel to QualityMode.BITRATE)
    }
    Dropdown(
        label = stringResource(R.string.advanced_quality),
        selectedText = modes.first { it.second == mode }.first,
        options = modes,
        onSelect = { picked ->
            onChange(
                options.copy(
                    quality = when (picked) {
                        QualityMode.PRESET -> null
                        QualityMode.CRF -> VideoQuality.Crf(DEFAULT_CRF.coerceIn(choices.crfRange ?: DEFAULT_CRF..DEFAULT_CRF))
                        QualityMode.BITRATE -> VideoQuality.Bitrate(DEFAULT_BITRATE_KBPS)
                    },
                ),
            )
        },
    )
    when (val q = options.quality) {
        is VideoQuality.Crf -> {
            val range = choices.crfRange ?: return
            Text(stringResource(R.string.advanced_crf_value, q.value), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = q.value.toFloat(),
                onValueChange = { onChange(options.copy(quality = VideoQuality.Crf(it.roundToInt().coerceIn(range)))) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
            )
            Text(stringResource(R.string.advanced_crf_hint), style = MaterialTheme.typography.bodySmall)
        }
        is VideoQuality.Bitrate -> {
            var text by rememberSaveable { mutableStateOf(q.kbps.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.filter { it.isDigit() }.take(6)
                    text.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(options.copy(quality = VideoQuality.Bitrate(it))) }
                },
                label = { Text(stringResource(R.string.advanced_bitrate_kbps)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        null -> Unit
    }
}

@Composable
private fun AudioControls(choices: AdvancedChoices, options: AdvancedOptions, onChange: (AdvancedOptions) -> Unit) {
    val presetDefault = stringResource(R.string.advanced_preset_default)
    Text(stringResource(R.string.advanced_audio), style = MaterialTheme.typography.titleSmall)
    if (choices.audioBitrates.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_audio_bitrate),
            selectedText = options.audioBitrateKbps?.let { stringResource(R.string.advanced_kbps, it) } ?: presetDefault,
            options = listOf<Pair<String, Int?>>(presetDefault to null) + choices.audioBitrates.map { stringResource(R.string.advanced_kbps, it) to it },
            onSelect = { onChange(options.copy(audioBitrateKbps = it)) },
        )
    }
    if (choices.sampleRates.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_sample_rate),
            selectedText = options.sampleRateHz?.let { stringResource(R.string.advanced_hz, it) } ?: presetDefault,
            options = listOf<Pair<String, Int?>>(presetDefault to null) + choices.sampleRates.map { stringResource(R.string.advanced_hz, it) to it },
            onSelect = { onChange(options.copy(sampleRateHz = it)) },
        )
    }
    if (choices.channelCounts.isNotEmpty()) {
        Dropdown(
            label = stringResource(R.string.advanced_channels),
            selectedText = options.channels?.let { channelName(it) } ?: presetDefault,
            options = listOf<Pair<String, Int?>>(presetDefault to null) + choices.channelCounts.map { channelName(it) to it },
            onSelect = { onChange(options.copy(channels = it)) },
        )
    }
}

@Composable
private fun channelName(count: Int): String = stringResource(if (count == 1) R.string.advanced_mono else R.string.advanced_stereo)

private fun formatFps(fps: Double): String = if (fps == fps.toInt().toDouble()) fps.toInt().toString() else fps.toString()
