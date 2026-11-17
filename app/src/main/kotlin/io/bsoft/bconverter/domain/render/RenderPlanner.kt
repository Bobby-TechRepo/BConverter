package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.timeline.PipFade
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.VideoProject

/** Turns a project into a [RenderPlan]: which inputs to read, the output canvas, and the audio layout. */
object RenderPlanner {
    private const val DEFAULT_FRAME_RATE = 30.0
    private const val DEFAULT_SAMPLE_RATE = 48_000

    /**
     * [keepSoundTrack]: when the export has sound and keeps the clips' own, always give it a sound track, silent if
     * no clip has any (used for render parts, which must all have the same streams to be joined; see [SegmentedRender]).
     */
    fun plan(project: VideoProject, keepSoundTrack: Boolean = false): RenderPlanResult {
        val clips = project.timeline.videoClips
        if (clips.isEmpty()) return RenderPlanResult.Failure(RenderError.NoVideo)
        val audioClips = project.timeline.audioClips

        val pipClips = project.timeline.pipClips
        (clips.map { it.source } + audioClips.map { it.source } + pipClips.map { it.source })
            .firstOrNull { it !is MediaSource.FileSource }
            ?.let { return RenderPlanResult.Failure(RenderError.SourceNotStaged(it)) }
        val metas = clips.map { project.sourceMetadata[it.source] ?: return RenderPlanResult.Failure(RenderError.MissingSourceInfo(it.source)) }
        val firstStream = metas.first().videoStreams.firstOrNull()
            ?: return RenderPlanResult.Failure(RenderError.MissingSourceInfo(clips.first().source))

        val export = project.export
        // Every clip's picture size is needed to measure its crop; the output follows the first clip as it looks after its transform.
        val streams = clips.mapIndexed { i, c ->
            metas[i].videoStreams.firstOrNull() ?: if (c.transform.isIdentity && c.kenBurns.isNone) null else return RenderPlanResult.Failure(RenderError.MissingSourceInfo(c.source))
        }
        val (firstW, firstH) = clips.first().transform.outputSize(firstStream.displayWidth, firstStream.displayHeight)
        val (shapedW, shapedH) = project.canvas.sizeFor(firstW, firstH)
        val canvas = Canvas(
            width = even(export.video.resolution?.width ?: shapedW),
            height = even(export.video.resolution?.height ?: shapedH),
            frameRate = export.video.frameRate ?: firstStream.frameRate ?: DEFAULT_FRAME_RATE,
        )
        clips.firstOrNull { it.reversed && !it.isFreeze && it.sourceEndMs - it.sourceStartMs > ReversePolicy.maxDurationMs(canvas.width, canvas.height, canvas.frameRate) }
            ?.let { return RenderPlanResult.Failure(RenderError.ReverseTooLong(it.id, ReversePolicy.maxDurationMs(canvas.width, canvas.height, canvas.frameRate))) }
        val pipMetas = pipClips.map { project.sourceMetadata[it.source] ?: return RenderPlanResult.Failure(RenderError.MissingSourceInfo(it.source)) }
        val inputs = clips.map { RenderInput(fileOf(it.source), it.sourceStartMs, it.sourceEndMs - it.sourceStartMs) } +
            audioClips.map { RenderInput(fileOf(it.source), it.sourceStartMs, it.sourceEndMs - it.sourceStartMs) } +
            pipClips.map { RenderInput(fileOf(it.source), it.sourceStartMs, it.sourceEndMs - it.sourceStartMs) }

        val audioFormat = export.audio?.let { AudioFormatSpec(it.sampleRateHz ?: DEFAULT_SAMPLE_RATE, if (it.channels == 1) 1 else 2) }
        val originalAudio = if (audioFormat != null && export.keepOriginalAudio && (keepSoundTrack || clips.indices.any { metas[it].hasAudio && !clips[it].audioMuted })) {
            clips.mapIndexed { i, c -> OriginalAudioSegment(if (metas[i].hasAudio && !c.audioMuted && !c.isFreeze) i else null, c.playbackSpeed, c.durationMs, c.volume, c.fadeInMs, c.fadeOutMs, c.reversed) }
        } else null
        val pipFirst = clips.size + audioClips.size
        val overlays = if (audioFormat != null) {
            audioClips.mapIndexed { k, a -> OverlayAudio(clips.size + k, a.volume, a.startTimeMs, a.playbackSpeed, a.fadeInMs, a.fadeOutMs, a.durationMs) } +
                // A picture-in-picture clip's own sound is mixed in like an audio clip.
                pipClips.mapIndexedNotNull { k, p -> if (pipMetas[k].hasAudio && p.volume > 0f) OverlayAudio(pipFirst + k, p.volume, p.startTimeMs, p.speed, PipFade.fitted(p).first, PipFade.fitted(p).second, p.durationMs) else null }
        } else emptyList()
        val pips = pipClips.mapIndexed { k, p ->
            val first = p.keyframes.firstOrNull()
            val widthPx = even(((first?.width ?: p.layout.width) * canvas.width).toInt())
            // A frame cannot follow a changing size.
            val sizeChanges = p.motion?.endWidth != null || p.keyframes.map { it.width }.distinct().size > 1
            val (fin, fout) = PipFade.fitted(p)
            PipSegment(
                pipFirst + k, p.startTimeMs, p.durationMs, widthPx, first?.x ?: p.layout.centerX, first?.y ?: p.layout.centerY, first?.opacity ?: p.layout.opacity, fin, fout,
                borderPx = if (p.shape != io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE || sizeChanges) 0 else even((p.layout.borderWidth * widthPx).toInt()).let { if (p.layout.borderWidth > 0f) maxOf(it, 2) else 0 }, borderRgb = p.layout.borderRgb, chromaKey = p.chromaKey, speed = p.speed, shape = p.shape, keyframes = p.keyframes, endWidthPx = p.motion?.endWidth?.let { even((it * canvas.width).toInt()) }, motion = p.motion,
            )
        }

        return RenderPlanResult.Success(
            RenderPlan(
                canvas = canvas,
                inputs = inputs,
                videoSegments = clips.mapIndexed { i, c -> VideoSegment(i, c.playbackSpeed, c.durationMs, c.startTimeMs, project.timeline.joinBefore(i), c.filter, c.transform, streams[i]?.displayWidth ?: 0, streams[i]?.displayHeight ?: 0, c.reversed, c.isFreeze, if (c.isFreeze) io.bsoft.bconverter.domain.transform.KenBurns.None else c.kenBurns, if (c.isFreeze || c.kenBurns.isNone) 0 else c.sourceEndMs - c.sourceStartMs) },
                originalAudio = originalAudio,
                overlays = overlays,
                audioFormat = audioFormat,
                durationMs = project.timeline.videoDurationMs,
                pips = pips,
                fit = project.canvas.fit,
                background = project.canvas.background,
            ),
        )
    }

    private fun fileOf(source: MediaSource) = (source as MediaSource.FileSource).file

    /** Encoders such as H.264 need even dimensions. */
    private fun even(value: Int): Int = maxOf(2, value - value % 2)
}
