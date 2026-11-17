package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.timeline.VideoProject
import java.io.File

/**
 * Step 19: which projects the device's own encoder pipeline (Media3 Transformer) can render exactly as FFmpeg would.
 * That path uses the hardware encoder, works on every ABI (FFmpegKit ships arm64 only) and needs far less memory,
 * so it is tried first; anything it cannot reproduce stays with FFmpeg. Today that is the common "trim and join"
 * project: plain cuts between unmodified clips, scaled into the canvas with black bars, to MP4 (H.264/HEVC + AAC).
 */
object HardwareRender {
    data class Clip(val file: File, val startMs: Long, val endMs: Long)

    data class Plan(
        val clips: List<Clip>,
        val width: Int,
        val height: Int,
        val videoCodec: VideoCodec,
        /** False: the output has no sound. */
        val keepSound: Boolean,
        /** Null lets the encoder choose the bitrate for the size and frame rate (CRF has no hardware equivalent). */
        val videoBitrateKbps: Int?,
        val durationMs: Long,
    )

    sealed interface Verdict {
        data class Eligible(val plan: Plan) : Verdict
        /** [reason] is for logs only. */
        data class NotEligible(val reason: String) : Verdict
    }

    private val VIDEO_CODECS = setOf(VideoCodec.H264, VideoCodec.HEVC)

    fun check(project: VideoProject, hasOverlays: Boolean, hasSubtitles: Boolean): Verdict {
        val export = project.export
        val timeline = project.timeline
        fun no(reason: String) = Verdict.NotEligible(reason)
        if (export.container != ContainerFormat.MP4) return no("container ${export.container}")
        if (export.video.codec !in VIDEO_CODECS) return no("video codec ${export.video.codec}")
        export.audio?.let { if (it.codec != AudioCodec.AAC) return no("audio codec ${it.codec}") }
        // The source frame rate is kept; a chosen one needs FFmpeg's frame-rate conversion.
        if (export.video.frameRate != null) return no("frame rate")
        if (export.video.pixelFormat != null || export.video.profile != null || export.video.level != null) return no("encoder options")
        if (hasOverlays) return no("text overlays")
        if (hasSubtitles) return no("subtitles")
        if (timeline.pipClips.isNotEmpty()) return no("picture-in-picture")
        if (timeline.audioClips.isNotEmpty()) return no("audio clips")
        if (project.canvas.fit != CanvasFit.FIT || project.canvas.background != CanvasBackground.Color(0)) return no("canvas style")
        val clips = timeline.videoClips
        if (clips.isEmpty()) return no("no video")
        val keepSound = export.audio != null && export.keepOriginalAudio
        clips.forEachIndexed { i, c ->
            if (c.source !is MediaSource.FileSource) return no("source not staged")
            if (i > 0 && timeline.joinBefore(i).isActive) return no("transition")
            if (c.playbackSpeed != 1f || c.reversed || c.isFreeze) return no("speed, reverse or freeze")
            if (!c.filter.isIdentity || !c.transform.isIdentity || !c.kenBurns.isNone) return no("clip effects")
            val meta = project.sourceMetadata[c.source] ?: return no("unknown source")
            if (!meta.hasVideo) return no("source without video")
            if (keepSound && (c.audioMuted || !meta.hasAudio || c.volume != 1f || c.fadeInMs > 0 || c.fadeOutMs > 0)) return no("clip sound edits")
        }
        val plan = (RenderPlanner.plan(project) as? RenderPlanResult.Success)?.plan ?: return no("cannot be planned")
        return Verdict.Eligible(
            Plan(
                clips = clips.map { Clip((it.source as MediaSource.FileSource).file, it.sourceStartMs, it.sourceEndMs) },
                width = plan.canvas.width, height = plan.canvas.height,
                videoCodec = export.video.codec, keepSound = keepSound,
                videoBitrateKbps = (export.video.quality as? VideoQuality.Bitrate)?.kbps,
                durationMs = plan.durationMs,
            ),
        )
    }
}
