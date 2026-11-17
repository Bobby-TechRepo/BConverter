package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.model.VideoStream
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.VideoProject
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
        sourceDuration: Long = sourceStart + spanMs, start: Long = 0, muted: Boolean = false,
    ) = VideoClip(id, source(id), sourceDuration, sourceStart, sourceStart + spanMs, speed, start, audioMuted = muted)

    fun audio(
        id: String, spanMs: Long = 5_000, start: Long = 0, volume: Float = 1f, sourceStart: Long = 0,
        sourceDuration: Long = sourceStart + spanMs, speed: Float = 1f,
    ) = AudioClip(id, source(id), sourceDuration, sourceStart, sourceStart + spanMs, volume, start, speed)

    fun timeline(vararg clips: VideoClip): Timeline = Timeline(videoClips = clips.toList()).normalized()

    fun withAudio(base: Timeline, vararg lanes: List<AudioClip>): Timeline =
        base.copy(audioTracks = lanes.mapIndexed { i, clips -> AudioTrack("lane-$i", clips) })

    fun ok(result: EditResult): Timeline = (result as EditResult.Success).timeline
    fun err(result: EditResult): EditError = (result as EditResult.Failure).error

    // ---- render fixtures ----
    fun meta(width: Int = 1920, height: Int = 1080, fps: Double? = 30.0, rotation: Int = 0, audio: Boolean = true) = MediaMetadata(
        10_000, ContainerFormat.MP4,
        listOf(VideoStream(0, "h264", width, height, fps, null, rotation)),
        if (audio) listOf(AudioStream(1, "aac", 44100, 2)) else emptyList(),
    )

    fun exportSettings(
        video: VideoSettings = VideoSettings(VideoCodec.H264, VideoQuality.Crf(23)),
        audio: AudioSettings? = AudioSettings(AudioCodec.AAC, 128),
        keepOriginalAudio: Boolean = true,
    ) = ExportSettings(ContainerFormat.MP4, video, audio, keepOriginalAudio)

    /** Every video clip's source gets [metadata] (return null to leave a source unknown). */
    fun project(
        timeline: Timeline,
        export: ExportSettings = exportSettings(),
        metadata: (MediaSource) -> MediaMetadata? = { meta() },
    ) = VideoProject(
        "p1", "Project", timeline, export,
        (timeline.videoClips.map { it.source } + timeline.pipClips.map { it.source }).mapNotNull { src -> metadata(src)?.let { src to it } }.toMap(),
    )
}
