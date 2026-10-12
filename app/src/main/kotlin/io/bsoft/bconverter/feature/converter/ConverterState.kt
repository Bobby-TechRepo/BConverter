package io.bsoft.bconverter.feature.converter

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ConversionSettings
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.VideoCodec

/** Shared by the converter and the audio extractor ([Converting] vs [Extracting] are their respective busy states). */
enum class ConverterStage { SelectInput, Probing, Configure, Converting, Extracting, Completed }

sealed interface ConverterMessage {
    data class InputProblem(val error: InputError) : ConverterMessage
    data object ProbeFailed : ConverterMessage
    data object NoVideoStream : ConverterMessage
    data object NoAudioStream : ConverterMessage
    data object NoEncodersAvailable : ConverterMessage
    data object ConversionFailed : ConverterMessage
    data object SaveFailed : ConverterMessage
}

data class SourceInfo(val displayName: String, val sizeBytes: Long, val metadata: MediaMetadata)

/** What the user chose; `videoCodec`/`audioCodec` null means automatic. The concrete settings are derived from it. */
data class ConverterForm(
    val preset: ConversionPreset,
    val container: ContainerFormat,
    val videoCodec: VideoCodec?,
    val audioCodec: AudioCodec?,
    val outputName: String,
)

data class ConverterUiState(
    val stage: ConverterStage = ConverterStage.SelectInput,
    val source: SourceInfo? = null,
    val form: ConverterForm? = null,
    /** Derived from [form]; null when no valid, encodable combination exists. */
    val settings: ConversionSettings? = null,
    val containers: List<ContainerFormat> = emptyList(),
    val videoCodecs: List<VideoCodec> = emptyList(),
    val audioCodecs: List<AudioCodec> = emptyList(),
    val issues: List<ConversionIssue> = emptyList(),
    val message: ConverterMessage? = null,
    val processing: ProcessingState = ProcessingState.Idle,
)

sealed interface ConverterEvent {
    data class RequestDestination(val fileName: String, val mimeType: String) : ConverterEvent
}
