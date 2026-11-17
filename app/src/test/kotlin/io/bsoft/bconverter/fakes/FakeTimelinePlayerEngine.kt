package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.PreviewSegment
import io.bsoft.bconverter.media.player.TimelinePlayerEngine
import io.bsoft.bconverter.media.player.TimelinePlayerListener

/** Records every command as a short string, e.g. "prepare:a,b", "seek:1@7000", "startAudio:m1@500x1.0". */
class FakeTimelinePlayerEngine : TimelinePlayerEngine {
    var listener: TimelinePlayerListener? = null; private set
    val calls = mutableListOf<String>()
    var released = false; private set

    override fun setListener(listener: TimelinePlayerListener?) { this.listener = listener }
    override fun prepare(segments: List<PreviewSegment>) { calls += "prepare:" + segments.joinToString(",") { it.clipId } }
    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun seekTo(segmentIndex: Int, sourcePositionMs: Long) { calls += "seek:$segmentIndex@$sourcePositionMs" }
    override fun startAudio(clip: AudioClip, sourcePositionMs: Long) { calls += "startAudio:${clip.id}@${sourcePositionMs}x${clip.volume}" }
    override fun stopAudio(clipId: String) { calls += "stopAudio:$clipId" }
    override fun stopAllAudio() { calls += "stopAllAudio" }
    override fun release() { released = true; calls += "release" }

    fun clear() = calls.clear()
}
