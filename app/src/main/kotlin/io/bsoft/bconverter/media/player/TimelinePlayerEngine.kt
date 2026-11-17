package io.bsoft.bconverter.media.player

import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.PreviewSegment

/**
 * Seam over the real player for timeline preview (spec §26). It plays the video track as a sequence of clipped segments
 * (each at its own speed) and sounds added audio clips on demand. Every source must be a prepared file. Main thread only.
 */
interface TimelinePlayerEngine {
    fun setListener(listener: TimelinePlayerListener?)
    fun prepare(segments: List<PreviewSegment>)
    fun play()
    fun pause()
    fun seekTo(segmentIndex: Int, sourcePositionMs: Long)
    /** Sounds [clip] from [sourcePositionMs] (of its own source) at its volume, until stopped or it runs out. */
    fun startAudio(clip: AudioClip, sourcePositionMs: Long)
    fun stopAudio(clipId: String)
    fun stopAllAudio()
    fun release()
}

/** A [TimelinePlayerEngine] whose picture a [androidx.media3.ui.PlayerView] can show. */
interface VideoPreviewEngine : TimelinePlayerEngine {
    val player: androidx.media3.common.Player
}

interface TimelinePlayerListener {
    /** The segments are loaded and playable (also after each re-prepare). */
    fun onReady()
    fun onIsPlayingChanged(isPlaying: Boolean)
    /** Regular progress: which segment is playing and where in its source file. */
    fun onVideoPosition(segmentIndex: Int, sourcePositionMs: Long)
    fun onEnded()
    fun onError(error: PlaybackError)
}
