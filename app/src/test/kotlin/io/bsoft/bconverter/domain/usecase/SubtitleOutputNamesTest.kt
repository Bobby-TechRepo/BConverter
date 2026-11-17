package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleOutputNamesTest {
    private fun name(base: String, stream: SubtitleStream, ordinal: Int, out: SubtitleOutput, used: MutableSet<String> = mutableSetOf()) =
        SubtitleOutputNames.fileName(base, stream, ordinal, out, used)

    @Test fun the_language_goes_between_the_name_and_the_extension() =
        assertEquals("movie.eng.srt", name("movie", SubtitleStream(2, "eng", "subrip"), 0, SubtitleOutput.SRT))

    @Test fun forced_tracks_are_marked() =
        assertEquals("movie.eng.forced.ass", name("movie", SubtitleStream(2, "eng", "ass", isForced = true), 0, SubtitleOutput.ASS))

    @Test fun a_stream_without_a_language_uses_just_the_name_first_and_its_number_after() {
        val used = mutableSetOf<String>()
        assertEquals("movie.srt", name("movie", SubtitleStream(2, null, "subrip"), 0, SubtitleOutput.SRT, used))
        assertEquals("movie.2.srt", name("movie", SubtitleStream(3, null, "subrip"), 1, SubtitleOutput.SRT, used))
    }

    @Test fun identical_names_are_made_unique() {
        val used = mutableSetOf<String>()
        assertEquals("movie.eng.srt", name("movie", SubtitleStream(2, "eng", "subrip"), 0, SubtitleOutput.SRT, used))
        assertEquals("movie.eng.2.srt", name("movie", SubtitleStream(3, "eng", "subrip"), 1, SubtitleOutput.SRT, used))
        assertEquals("movie.eng.3.srt", name("movie", SubtitleStream(4, "eng", "subrip"), 2, SubtitleOutput.SRT, used))
    }
}
