package io.bsoft.bconverter.media.player

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.PreviewMapper
import io.bsoft.bconverter.domain.timeline.PreviewSegment
import io.bsoft.bconverter.domain.timeline.SeekRange
import io.bsoft.bconverter.domain.timeline.Timeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** [positionMs] is a timeline time; [durationMs] is the length of the video track (audio running past it is not previewed, as in export). */
data class TimelinePlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: PlaybackError? = null,
) {
    /** True while the position is in the blank area after the last video frame: nothing is shown. */
    val isBlank: Boolean get() = positionMs > durationMs
}

/**
 * Timeline -> playback state -> player (spec §59). The timeline is the source of truth: this class translates it to
 * segment commands for a [TimelinePlayerEngine] and the engine's progress back into timeline time. Audio clips are started
 * and stopped as the playhead enters and leaves them. Main thread only, like the player.
 */
class TimelinePlaybackController(private val engine: TimelinePlayerEngine) : TimelinePlayerListener {
    private val _state = MutableStateFlow(TimelinePlaybackState())
    val state: StateFlow<TimelinePlaybackState> = _state.asStateFlow()

    private var timeline = Timeline()
    private var segments: List<PreviewSegment> = emptyList()
    private val sounding = LinkedHashSet<String>()
    /** True from play() (or the player reporting that it plays) until pause, end, error or an edit. */
    private var wantPlaying = false

    init { engine.setListener(this) }

    /** (Re)loads the timeline. The position is kept (within the new length); playback stops. */
    fun setTimeline(timeline: Timeline) {
        if (wantPlaying) engine.pause()
        wantPlaying = false
        silence()
        this.timeline = timeline
        segments = PreviewMapper.segments(timeline)
        val position = _state.value.positionMs
        when {
            segments.isEmpty() -> _state.value = TimelinePlaybackState()
            segments.any { it.source !is MediaSource.FileSource } -> _state.value = TimelinePlaybackState(
                status = PlaybackStatus.Error, positionMs = SeekRange.clamp(timeline, position),
                durationMs = timeline.videoDurationMs, error = PlaybackError.SourceNotFound,
            )
            else -> {
                _state.value = TimelinePlaybackState(
                    status = PlaybackStatus.Loading, positionMs = SeekRange.clamp(timeline, position), durationMs = timeline.videoDurationMs,
                )
                engine.prepare(segments)
            }
        }
    }

    fun play() {
        when (_state.value.status) {
            PlaybackStatus.Ready -> if (_state.value.isBlank) restartFromBeginning() else startPlayback()
            PlaybackStatus.Ended -> {
                _state.update { it.copy(status = PlaybackStatus.Ready, positionMs = 0) }
                seekEngine(0)
                startPlayback()
            }
            else -> Unit
        }
    }

    private fun restartFromBeginning() {
        _state.update { it.copy(positionMs = 0) }
        seekEngine(0)
        startPlayback()
    }

    private fun startPlayback() {
        wantPlaying = true
        engine.play()
        syncAudio(_state.value.positionMs)
    }

    fun pause() {
        if (_state.value.status != PlaybackStatus.Ready || !(wantPlaying || _state.value.isPlaying)) return
        wantPlaying = false
        engine.pause()
        silence()
    }

    fun togglePlayPause() { if (wantPlaying || _state.value.isPlaying) pause() else play() }

    fun seekTo(positionMs: Long) {
        val s = _state.value
        if (s.status != PlaybackStatus.Ready && s.status != PlaybackStatus.Ended) return
        val target = SeekRange.clamp(timeline, positionMs)
        _state.update { it.copy(positionMs = target, status = if (it.status == PlaybackStatus.Ended && target < it.durationMs) PlaybackStatus.Ready else it.status) }
        // Nothing plays in the blank area: stop there instead of letting the player run on to its end.
        if (wantPlaying && SeekRange.isBlank(timeline, target)) { wantPlaying = false; engine.pause(); silence() }
        seekEngine(target)
        if (wantPlaying) { silence(); syncAudio(target) }
    }

    fun release() {
        engine.release()
        engine.setListener(null)
        sounding.clear()
        wantPlaying = false
        segments = emptyList()
        timeline = Timeline()
        _state.value = TimelinePlaybackState()
    }

    // ---- helpers ----
    private fun seekEngine(timelineMs: Long) {
        val last = (_state.value.durationMs - 1).coerceAtLeast(0)
        PreviewMapper.positionAt(timeline, timelineMs.coerceIn(0, last))?.let { engine.seekTo(it.clipIndex, it.sourcePositionMs) }
    }

    private fun silence() { engine.stopAllAudio(); sounding.clear() }

    /** Starts the audio clips that should sound at [timelineMs] and stops those that should not. */
    private fun syncAudio(timelineMs: Long) {
        val active = PreviewMapper.activeAudioAt(timeline, timelineMs).filter { it.clip.source is MediaSource.FileSource }
        val activeIds = active.map { it.clip.id }.toSet()
        sounding.filter { it !in activeIds }.forEach { engine.stopAudio(it); sounding.remove(it) }
        active.filter { it.clip.id !in sounding }.forEach { engine.startAudio(it.clip, it.sourcePositionMs); sounding += it.clip.id }
    }

    // ---- engine callbacks ----
    override fun onReady() {
        if (_state.value.status != PlaybackStatus.Loading) return
        _state.update { it.copy(status = PlaybackStatus.Ready) }
        if (_state.value.positionMs > 0) seekEngine(_state.value.positionMs)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (_state.value.status != PlaybackStatus.Ready) return
        _state.update { it.copy(isPlaying = isPlaying) }
        wantPlaying = isPlaying
        if (isPlaying) syncAudio(_state.value.positionMs) else silence()
    }

    override fun onVideoPosition(segmentIndex: Int, sourcePositionMs: Long) {
        if (_state.value.status != PlaybackStatus.Ready) return
        val clip = timeline.videoClips.getOrNull(segmentIndex) ?: return
        val position = PreviewMapper.timelineTimeOf(clip, sourcePositionMs)
        _state.update { it.copy(positionMs = position) }
        if (wantPlaying) syncAudio(position)
    }

    override fun onEnded() {
        if (_state.value.status != PlaybackStatus.Ready) return
        wantPlaying = false
        _state.update { it.copy(status = PlaybackStatus.Ended, isPlaying = false, positionMs = it.durationMs) }
        silence()
    }

    override fun onError(error: PlaybackError) {
        if (_state.value.status == PlaybackStatus.Idle) return
        wantPlaying = false
        _state.update { it.copy(status = PlaybackStatus.Error, isPlaying = false, error = error) }
        silence()
    }
}
