package io.bsoft.bconverter.feature.subtitleextractor

import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.SourceInfo

data class SubtitleExtractorUiState(
    val stage: ConverterStage = ConverterStage.SelectInput,
    val source: SourceInfo? = null,
    /** Chosen tracks (by position among the subtitle streams) and the format each is saved as. */
    val selection: Map<Int, SubtitleOutput> = emptyMap(),
    val baseName: String = "",
    val issues: List<ConversionIssue> = emptyList(),
    val message: ConverterMessage? = null,
    /** 0-based number of the file being saved, and how many there are. */
    val currentFile: Int = 0,
    val totalFiles: Int = 0,
    val progress: Float = 0f,
    val saved: SavedOutput? = null,
    val savedCount: Int = 0,
    val failedCount: Int = 0,
)
