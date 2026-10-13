package io.bsoft.bconverter.media.player

enum class PlaybackStatus { Idle, Loading, Ready, Ended, Error }

enum class PlaybackError { SourceNotFound, Unsupported, Decoder, Unknown }

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: PlaybackError? = null,
) {
    /** 0..1; 0 while the duration is unknown. */
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
