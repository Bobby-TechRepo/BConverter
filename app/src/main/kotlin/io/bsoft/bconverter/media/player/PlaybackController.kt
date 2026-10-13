package io.bsoft.bconverter.media.player

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Playback state machine in front of a [PlayerEngine]: validates commands (play only when ready, seek clamped to the
 * media, replay after the end) and publishes one [PlaybackState]. Main-thread only, like the player itself.
 */
class PlaybackController(private val engine: PlayerEngine) : PlayerEngineListener {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init { engine.setListener(this) }

    fun load(file: File) {
        _state.value = PlaybackState(status = PlaybackStatus.Loading)
        engine.prepare(file)
    }

    fun play() {
        when (_state.value.status) {
            PlaybackStatus.Ready -> engine.play()
            PlaybackStatus.Ended -> {
                engine.seekTo(0)
                _state.update { it.copy(status = PlaybackStatus.Ready, positionMs = 0) }
                engine.play()
            }
            else -> Unit
        }
    }

    fun pause() {
        val s = _state.value
        if (s.status == PlaybackStatus.Ready && s.isPlaying) engine.pause()
    }

    fun togglePlayPause() { if (_state.value.isPlaying) pause() else play() }

    fun seekTo(positionMs: Long) {
        val s = _state.value
        if ((s.status != PlaybackStatus.Ready && s.status != PlaybackStatus.Ended) || s.durationMs <= 0) return
        val target = positionMs.coerceIn(0, s.durationMs)
        engine.seekTo(target)
        _state.update {
            it.copy(positionMs = target, status = if (it.status == PlaybackStatus.Ended && target < it.durationMs) PlaybackStatus.Ready else it.status)
        }
    }

    fun release() {
        engine.release()
        engine.setListener(null)
        _state.value = PlaybackState()
    }

    // ---- engine callbacks ----
    override fun onReady(durationMs: Long) {
        _state.update {
            if (it.status == PlaybackStatus.Loading || it.status == PlaybackStatus.Ready) it.copy(status = PlaybackStatus.Ready, durationMs = durationMs) else it
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.update { if (it.status == PlaybackStatus.Ready) it.copy(isPlaying = isPlaying) else it }
    }

    override fun onPosition(positionMs: Long) {
        _state.update {
            if (it.status == PlaybackStatus.Idle) it
            else it.copy(positionMs = if (it.durationMs > 0) positionMs.coerceIn(0, it.durationMs) else maxOf(0, positionMs))
        }
    }

    override fun onEnded() {
        _state.update {
            if (it.status == PlaybackStatus.Ready) it.copy(status = PlaybackStatus.Ended, isPlaying = false, positionMs = it.durationMs) else it
        }
    }

    override fun onError(error: PlaybackError) {
        _state.update { if (it.status == PlaybackStatus.Idle) it else it.copy(status = PlaybackStatus.Error, isPlaying = false, error = error) }
    }
}
