package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleOutput

data class SubtitleJobPlan(val settings: SubtitleExtractionSettings, val fileName: String)

/** Turns the user's track→format choices into an ordered list of single-track jobs with unique file names. */
class SubtitleBatchPlanner(private val planner: SubtitleExtractionPlanner = SubtitleExtractionPlanner()) {
    fun plan(baseName: String, metadata: MediaMetadata, selection: Map<Int, SubtitleOutput>): List<SubtitleJobPlan> {
        val used = mutableSetOf<String>()
        return selection.toSortedMap().mapNotNull { (ordinal, output) ->
            val settings = planner.settingsFor(metadata, ordinal, output) ?: return@mapNotNull null
            val stream = metadata.subtitleStreams[ordinal]
            SubtitleJobPlan(settings, SubtitleOutputNames.fileName(baseName.trim(), stream, ordinal, output, used))
        }
    }
}
