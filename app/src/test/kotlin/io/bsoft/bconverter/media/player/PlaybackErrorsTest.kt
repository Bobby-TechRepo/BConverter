package io.bsoft.bconverter.media.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorsTest {
    @Test fun a_missing_file_is_source_not_found() =
        assertEquals(PlaybackError.SourceNotFound, PlaybackErrors.fromMedia3(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))

    @Test fun malformed_or_unsupported_media_is_unsupported() = listOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    ).forEach { assertEquals(it.toString(), PlaybackError.Unsupported, PlaybackErrors.fromMedia3(it)) }

    @Test fun decoder_problems_are_decoder_errors() = listOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
    ).forEach { assertEquals(it.toString(), PlaybackError.Decoder, PlaybackErrors.fromMedia3(it)) }

    @Test fun anything_else_is_unknown() =
        assertEquals(PlaybackError.Unknown, PlaybackErrors.fromMedia3(PlaybackException.ERROR_CODE_UNSPECIFIED))
}
