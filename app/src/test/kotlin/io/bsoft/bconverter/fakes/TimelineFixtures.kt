package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.AudioTrack
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.timeline.EditResult
import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import java.io.File

/** Deterministic ids: "id-1", "id-2", … */
class SequentialIds : IdGenerator {
    private var n = 0
    override fun next(): String = "id-${++n}"
}

object TL {
    fun source(name: String = "a.mp4"): MediaSource = MediaSource.FileSource(File("/tmp/$name"))

    /** A video clip taking [spanMs] of its source, played at [speed]. */
    fun video(
        id: String, spanMs: Long = 10_000, speed: Float = 1f, sourceStart: Long = 0,
        sourceDuration: Long = sourceStart + spanMs, start: Long = 0,
    ) = VideoClip(id, source(id), sourceDuration, sourceStart, sourceStart + spanMs, speed, start)

    fun audio(
        id: String, spanMs: Long = 5_000, start: Long = 0, volume: Float = 1f, sourceStart: Long = 0,
        sourceDuration: Long = sourceStart + spanMs,
    ) = AudioClip(id, source(id), sourceDuration, sourceStart, sourceStart + spanMs, volume, start)

    fun timeline(vararg clips: VideoClip): Timeline = Timeline(videoClips = clips.toList()).normalized()

    fun withAudio(base: Timeline, vararg lanes: List<AudioClip>): Timeline =
        base.copy(audioTracks = lanes.mapIndexed { i, clips -> AudioTrack("lane-$i", clips) })

    fun ok(result: EditResult): Timeline = (result as EditResult.Success).timeline
    fun err(result: EditResult): EditError = (result as EditResult.Failure).error
}
