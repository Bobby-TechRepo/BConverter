package io.bsoft.bconverter.feature.audioextractor

import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.SourceInfo

/** What the user chose. The concrete settings are derived from it. */
data class ExtractorForm(
    val streamOrdinal: Int,
    val format: AudioFormat,
    /** Copy the stream without re-encoding when possible. */
    val preferCopy: Boolean,
    val outputName: String,
)

data class AudioExtractorUiState(
    val stage: ConverterStage = ConverterStage.SelectInput,
    val source: SourceInfo? = null,
    val form: ExtractorForm? = null,
    val settings: AudioExtractionSettings? = null,
    val formats: List<AudioFormat> = emptyList(),
    /** Whether the chosen format can be produced by stream copy for the chosen track. */
    val canCopy: Boolean = false,
    val issues: List<ConversionIssue> = emptyList(),
    val message: ConverterMessage? = null,
    val processing: ProcessingState = ProcessingState.Idle,
)
