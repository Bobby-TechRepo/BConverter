package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleBatchPlannerTest {
    private val meta = MediaMetadata(
        1000, ContainerFormat.MKV,
        subtitleStreams = listOf(
            SubtitleStream(2, "eng", "subrip"), SubtitleStream(3, "eng", "ass", isForced = true),
            SubtitleStream(4, "fra", "subrip"), SubtitleStream(5, "eng", "subrip"),
        ),
    )
    private val planner = SubtitleBatchPlanner()

    @Test fun selected_tracks_become_jobs_in_track_order_with_file_names() {
        val plan = planner.plan("movie", meta, mapOf(2 to SubtitleOutput.SRT, 0 to SubtitleOutput.ASS))
        assertEquals(listOf("movie.eng.ass", "movie.fra.srt"), plan.map { it.fileName })
        assertEquals(listOf(SubtitleExtractionSettings(0, SubtitleOutput.ASS), SubtitleExtractionSettings(2, SubtitleOutput.SRT)), plan.map { it.settings })
    }

    @Test fun tracks_that_would_share_a_name_are_numbered() =
        assertEquals(listOf("movie.eng.srt", "movie.eng.2.srt"), planner.plan("movie", meta, mapOf(0 to SubtitleOutput.SRT, 3 to SubtitleOutput.SRT)).map { it.fileName })

    @Test fun forced_tracks_are_named_so() =
        assertEquals(listOf("movie.eng.forced.srt"), planner.plan("movie", meta, mapOf(1 to SubtitleOutput.SRT)).map { it.fileName })

    @Test fun unknown_tracks_and_unavailable_outputs_are_skipped() =
        assertEquals(emptyList<Any>(), planner.plan("movie", meta, mapOf(9 to SubtitleOutput.SRT, 0 to SubtitleOutput.SUP)))
}
