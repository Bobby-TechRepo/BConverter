package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.model.VideoStream
import io.bsoft.bconverter.domain.model.crfRange
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.JoinEffect
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import java.io.File

/**
 * Rendering a long project in parts (step 18, P2). One FFmpeg run over the whole timeline opens a decoder for every
 * clip at once, so memory grows with the clip count and long projects run out of it. Instead:
 *
 * 1. the main video track is rendered in parts of at most [MAX_CLIPS_PER_PART] clips, each to a high-quality
 *    intermediate file at the final canvas size and frame rate (with the clips' own sound as PCM, so the parts join
 *    without audio gaps);
 * 2. the parts are joined without re-encoding (FFmpeg's concat demuxer);
 * 3. the final pass takes that one file as the only main-track clip and adds what spans the whole timeline:
 *    audio clips, picture-in-picture, text overlays and subtitles.
 *
 * Parts are only cut at plain cuts, never inside a transition, so every part renders exactly what the single pass
 * would. At most one part's decoders plus the final pass's inputs are open at any time.
 */
object SegmentedRender {
    const val MAX_CLIPS_PER_PART = 6

    /** How much the intermediate quality is raised over the export's, so the second encode loses as little as possible. */
    private const val CRF_HEADROOM = 6
    private const val BITRATE_HEADROOM = 2

    data class Part(val project: VideoProject, val file: File, val durationMs: Long)

    data class Plan(
        val parts: List<Part>,
        /** The joined parts; the final pass's only main-track clip. */
        val joinedFile: File,
        /** The concat demuxer's list of parts. */
        val listFile: File,
        val finalProject: VideoProject,
        /** True when the parts carry the clips' sound; each part then always has a sound track (silence if needed). */
        val partsHaveSound: Boolean,
        val durationMs: Long,
    )

    /** Clip index ranges of the parts; a new part starts only at a plain cut once the current one is full. */
    fun partRanges(timeline: Timeline, maxClips: Int = MAX_CLIPS_PER_PART): List<IntRange> {
        val count = timeline.videoClips.size
        if (count == 0) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = 0
        for (i in 1 until count) {
            if (i - start >= maxClips && timeline.joinBefore(i) == JoinEffect.None) {
                ranges.add(start until i)
                start = i
            }
        }
        ranges.add(start until count)
        return ranges
    }

    fun needsParts(project: VideoProject, maxClips: Int = MAX_CLIPS_PER_PART): Boolean = partRanges(project.timeline, maxClips).size > 1

    /**
     * The parts and the final pass for [project], whose single-pass plan is [plan]; null when it fits in one pass.
     * Intermediate files go to [workDir], named after [baseName].
     */
    fun plan(project: VideoProject, plan: RenderPlan, workDir: File, baseName: String, maxClips: Int = MAX_CLIPS_PER_PART): Plan? {
        val ranges = partRanges(project.timeline, maxClips)
        if (ranges.size < 2) return null
        val canvas = plan.canvas
        val sound = plan.originalAudio != null && plan.audioFormat != null
        val partExport = ExportSettings(
            container = ContainerFormat.MKV,
            video = intermediateVideo(project.export.video, canvas),
            audio = if (sound) AudioSettings(AudioCodec.PCM, null, plan.audioFormat!!.sampleRateHz, plan.audioFormat.channels) else null,
            keepOriginalAudio = sound,
        )
        val parts = ranges.mapIndexed { n, range ->
            val timeline = Timeline(videoClips = project.timeline.videoClips.slice(range)).normalized()
            val partProject = project.copy(
                id = "${project.id}-part${n + 1}",
                timeline = timeline,
                export = partExport,
                subtitleTracks = emptyList(),
                textOverlays = emptyList(),
            )
            Part(partProject, File(workDir, "$baseName.part${n + 1}.mkv"), timeline.videoDurationMs)
        }

        val joined = File(workDir, "$baseName.joined.mkv")
        val duration = parts.sumOf { it.durationMs }
        val base = VideoClip("segmented-base", MediaSource.FileSource(joined), duration, 0, duration)
        val baseMetadata = MediaMetadata(
            duration, ContainerFormat.MKV,
            listOf(VideoStream(0, null, canvas.width, canvas.height, canvas.frameRate)),
            if (sound) listOf(AudioStream(1, "pcm_s16le", plan.audioFormat!!.sampleRateHz, plan.audioFormat.channels)) else emptyList(),
        )
        val finalProject = project.copy(
            timeline = project.timeline.copy(videoClips = listOf(base)).normalized(),
            export = project.export.copy(video = project.export.video.copy(resolution = Resolution(canvas.width, canvas.height), frameRate = canvas.frameRate)),
            sourceMetadata = project.sourceMetadata + (base.source to baseMetadata),
        )
        return Plan(parts, joined, File(workDir, "$baseName.parts.txt"), finalProject, sound, duration)
    }

    /** The concat demuxer's list: one quoted path per line (a quote in a path is written as `'\''`). */
    fun concatList(files: List<File>): String = files.joinToString("\n", postfix = "\n") { "file '" + it.absolutePath.replace("'", "'\\''") + "'" }

    /** The export's encoder at a higher quality, fixed to the canvas so every part has identical stream parameters. */
    internal fun intermediateVideo(video: VideoSettings, canvas: Canvas): VideoSettings {
        val quality = when (val q = video.quality) {
            is VideoQuality.Crf -> VideoQuality.Crf(maxOf(video.codec.crfRange()?.first ?: 0, q.value - CRF_HEADROOM))
            is VideoQuality.Bitrate -> VideoQuality.Bitrate(q.kbps * BITRATE_HEADROOM)
        }
        // The level is dropped: a higher bitrate may not fit the export's level, and the parts are never played back.
        return video.copy(quality = quality, resolution = Resolution(canvas.width, canvas.height), frameRate = canvas.frameRate, level = null)
    }
}
