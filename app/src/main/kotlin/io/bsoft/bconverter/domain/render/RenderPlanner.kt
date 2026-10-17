package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.VideoProject

/** Turns a project into a [RenderPlan]: which inputs to read, the output canvas, and the audio layout. */
object RenderPlanner {
    private const val DEFAULT_FRAME_RATE = 30.0
    private const val DEFAULT_SAMPLE_RATE = 48_000

    fun plan(project: VideoProject): RenderPlanResult {
        val clips = project.timeline.videoClips
        if (clips.isEmpty()) return RenderPlanResult.Failure(RenderError.NoVideo)
        val audioClips = project.timeline.audioClips

        (clips.map { it.source } + audioClips.map { it.source })
            .firstOrNull { it !is MediaSource.FileSource }
            ?.let { return RenderPlanResult.Failure(RenderError.SourceNotStaged(it)) }
        val metas = clips.map { project.sourceMetadata[it.source] ?: return RenderPlanResult.Failure(RenderError.MissingSourceInfo(it.source)) }
        val firstStream = metas.first().videoStreams.firstOrNull()
            ?: return RenderPlanResult.Failure(RenderError.MissingSourceInfo(clips.first().source))

        val export = project.export
        val canvas = Canvas(
            width = even(export.video.resolution?.width ?: firstStream.displayWidth),
            height = even(export.video.resolution?.height ?: firstStream.displayHeight),
            frameRate = export.video.frameRate ?: firstStream.frameRate ?: DEFAULT_FRAME_RATE,
        )
        val inputs = clips.map { RenderInput(fileOf(it.source), it.sourceStartMs, it.sourceEndMs - it.sourceStartMs) } +
            audioClips.map { RenderInput(fileOf(it.source), it.sourceStartMs, it.durationMs) }

        val audioFormat = export.audio?.let { AudioFormatSpec(it.sampleRateHz ?: DEFAULT_SAMPLE_RATE, if (it.channels == 1) 1 else 2) }
        val originalAudio = if (audioFormat != null && export.keepOriginalAudio && metas.any { it.hasAudio }) {
            clips.mapIndexed { i, c -> OriginalAudioSegment(if (metas[i].hasAudio) i else null, c.playbackSpeed, c.durationMs) }
        } else null
        val overlays = if (audioFormat != null) {
            audioClips.mapIndexed { k, a -> OverlayAudio(clips.size + k, a.volume, a.startTimeMs) }
        } else emptyList()

        return RenderPlanResult.Success(
            RenderPlan(
                canvas = canvas,
                inputs = inputs,
                videoSegments = clips.mapIndexed { i, c -> VideoSegment(i, c.playbackSpeed) },
                originalAudio = originalAudio,
                overlays = overlays,
                audioFormat = audioFormat,
                durationMs = project.timeline.videoDurationMs,
            ),
        )
    }

    private fun fileOf(source: MediaSource) = (source as MediaSource.FileSource).file

    /** Encoders such as H.264 need even dimensions. */
    private fun even(value: Int): Int = maxOf(2, value - value % 2)
}
