package io.bsoft.bconverter.media.player

import java.io.File

/** Thin seam over the real player (Media3), so playback logic is testable without one. Main-thread only. */
interface PlayerEngine {
    fun setListener(listener: PlayerEngineListener?)
    fun prepare(file: File)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

/** What the engine reports back. */
interface PlayerEngineListener {
    /** Called whenever the player is ready to play, including after each seek; [durationMs] is 0 if unknown. */
    fun onReady(durationMs: Long)
    fun onIsPlayingChanged(isPlaying: Boolean)
    fun onPosition(positionMs: Long)
    fun onEnded()
    fun onError(error: PlaybackError)
}
