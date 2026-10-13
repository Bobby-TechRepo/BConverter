package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.media.player.PlayerEngine
import io.bsoft.bconverter.media.player.PlayerEngineListener
import java.io.File

class FakePlayerEngine : PlayerEngine {
    var listener: PlayerEngineListener? = null
    val prepared = mutableListOf<File>()
    /** Commands received, e.g. "play", "pause", "seek:4000", "release". */
    val calls = mutableListOf<String>()

    override fun setListener(listener: PlayerEngineListener?) { this.listener = listener }
    override fun prepare(file: File) { prepared += file }
    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
    override fun release() { calls += "release" }
}
