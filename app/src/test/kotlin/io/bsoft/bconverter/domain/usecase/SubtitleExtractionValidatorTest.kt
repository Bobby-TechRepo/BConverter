package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleExtractionValidatorTest {
    private val validator = SubtitleExtractionValidator(SubtitleExtractionPlanner())
    private val meta = MediaMetadata(1000, ContainerFormat.MKV, subtitleStreams = listOf(SubtitleStream(2, "eng", "subrip"), SubtitleStream(3, "eng", "hdmv_pgs_subtitle")))
    private fun check(settings: SubtitleExtractionSettings, m: MediaMetadata = meta, name: String = "movie") = validator.validate(settings, m, name)

    @Test fun a_valid_request_has_no_issues() = assertEquals(emptyList<ConversionIssue>(), check(SubtitleExtractionSettings(0, SubtitleOutput.SRT)))

    @Test fun a_source_without_subtitles_is_reported() =
        assertEquals(listOf(ConversionIssue.SourceHasNoSubtitles), check(SubtitleExtractionSettings(0, SubtitleOutput.SRT), MediaMetadata(1000, ContainerFormat.MKV)))

    @Test fun a_missing_stream_is_reported() = assertEquals(listOf(ConversionIssue.InvalidSubtitleStream), check(SubtitleExtractionSettings(7, SubtitleOutput.SRT)))

    @Test fun an_output_the_stream_cannot_produce_is_reported() =
        assertEquals(listOf(ConversionIssue.SubtitleOutputNotAvailable), check(SubtitleExtractionSettings(1, SubtitleOutput.SRT)))

    @Test fun a_bad_file_name_is_reported() =
        assertEquals(listOf(ConversionIssue.InvalidOutputName), check(SubtitleExtractionSettings(0, SubtitleOutput.SRT), name = "a/b"))
}
