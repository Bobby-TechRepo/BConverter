package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings

/** Reports every problem with a subtitle-extraction configuration; empty means valid. */
class SubtitleExtractionValidator(private val planner: SubtitleExtractionPlanner) {
    fun validate(settings: SubtitleExtractionSettings, metadata: MediaMetadata, outputName: String): List<ConversionIssue> = buildList {
        val stream = metadata.subtitleStreams.getOrNull(settings.streamOrdinal)
        if (metadata.subtitleStreams.isEmpty()) add(ConversionIssue.SourceHasNoSubtitles)
        else if (stream == null) add(ConversionIssue.InvalidSubtitleStream)
        else if (settings.output !in planner.outputsFor(stream)) add(ConversionIssue.SubtitleOutputNotAvailable)
        if (!OutputNames.isValid(outputName)) add(ConversionIssue.InvalidOutputName)
    }
}
