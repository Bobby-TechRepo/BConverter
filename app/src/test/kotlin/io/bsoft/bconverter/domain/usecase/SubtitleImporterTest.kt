package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.domain.model.ContentInfo
import io.bsoft.bconverter.domain.subtitle.SubtitleError
import io.bsoft.bconverter.domain.subtitle.SubtitleFormat
import io.bsoft.bconverter.fakes.FakeContentReader
import io.bsoft.bconverter.fakes.SequentialIds
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleImporterTest {
    private val reader = FakeContentReader()
    private val importer = SubtitleImporter(reader, SequentialIds())
    private val uri = Uri.parse("content://docs/movie.srt")
    private val srt = "1\n00:00:01,000 --> 00:00:02,000\nHéllo\n"

    private fun put(bytes: ByteArray, name: String? = "movie.srt", size: Long? = bytes.size.toLong()) =
        reader.put(uri, FakeContentReader.Entry(ContentInfo(name, null, size), bytes))
    private fun imported(r: SubtitleImportResult) = (r as SubtitleImportResult.Imported)

    @Test fun a_utf8_srt_file_becomes_a_track_named_after_the_file() {
        put(srt.toByteArray())
        val t = imported(importer.import(uri)).track
        assertEquals("movie", t.name); assertEquals(SubtitleFormat.SRT, t.sourceFormat); assertEquals("Héllo", t.cues.single().text)
    }

    @Test fun a_utf8_byte_order_mark_is_ignored() {
        put(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + srt.toByteArray())
        assertEquals(1, imported(importer.import(uri)).track.cues.size)
    }

    @Test fun utf16_with_a_byte_order_mark_is_read() {
        put(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + srt.toByteArray(Charsets.UTF_16LE))
        assertEquals("Héllo", imported(importer.import(uri)).track.cues.single().text)
    }

    @Test fun legacy_single_byte_text_falls_back_to_latin1() {
        put(srt.toByteArray(Charsets.ISO_8859_1))
        assertEquals("Héllo", imported(importer.import(uri)).track.cues.single().text)
    }

    @Test fun the_format_is_detected_from_the_content_even_with_a_wrong_extension() {
        put(srt.toByteArray(), name = "movie.txt")
        assertEquals(SubtitleFormat.SRT, imported(importer.import(uri)).track.sourceFormat)
    }

    @Test fun an_empty_file_fails_as_empty() {
        put(ByteArray(0)); assertEquals(SubtitleImportResult.Failed(SubtitleImportFailure.Parse(SubtitleError.Empty)), importer.import(uri))
    }

    @Test fun something_that_is_not_a_subtitle_fails_as_unknown() {
        put("just some words".toByteArray(), name = "notes.txt"); assertEquals(SubtitleImportResult.Failed(SubtitleImportFailure.Parse(SubtitleError.UnknownFormat)), importer.import(uri))
    }

    @Test fun a_missing_file_fails_as_unreadable() = assertEquals(SubtitleImportResult.Failed(SubtitleImportFailure.Unreadable), importer.import(uri))

    @Test fun a_huge_file_is_refused_without_reading_it() {
        put(srt.toByteArray(), size = 50L * 1024 * 1024)
        assertEquals(SubtitleImportResult.Failed(SubtitleImportFailure.TooLarge), importer.import(uri)); assertEquals(0, reader.openCount)
    }

    @Test fun a_stream_larger_than_reported_is_refused_too() {
        put(ByteArray(6 * 1024 * 1024) { 'a'.code.toByte() }, size = null)
        assertEquals(SubtitleImportResult.Failed(SubtitleImportFailure.TooLarge), importer.import(uri))
    }

    @Test fun parse_warnings_are_passed_on() {
        put(("1\n00:00:01,000 --> 00:00:02,000\nok\n\n2\nbroken\ntext\n").toByteArray())
        assertTrue(imported(importer.import(uri)).warnings.isNotEmpty())
    }

    @Test fun each_import_gets_new_ids() {
        put(srt.toByteArray())
        assertTrue(imported(importer.import(uri)).track.id != imported(importer.import(uri)).track.id)
    }
}
