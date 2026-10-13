package io.bsoft.bconverter.media.player

import androidx.media3.common.PlaybackException

object PlaybackErrors {
    fun fromMedia3(errorCode: Int): PlaybackError = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError.SourceNotFound
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackError.Unsupported
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackError.Decoder
        else -> PlaybackError.Unknown
    }
}
