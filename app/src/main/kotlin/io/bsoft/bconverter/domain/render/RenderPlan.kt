package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.model.MediaSource
import java.io.File

/** The output picture: every clip is scaled/padded to this size and conformed to this frame rate. */
data class Canvas(val width: Int, val height: Int, val frameRate: Double)

/** One FFmpeg input: [durationMs] of [file] starting at [seekMs] (source time). */
data class RenderInput(val file: File, val seekMs: Long, val durationMs: Long)

data class VideoSegment(val inputIndex: Int, val speed: Float)

/** The original sound of one video clip; [inputIndex] is null when the clip has no audio (silence is generated). */
data class OriginalAudioSegment(val inputIndex: Int?, val speed: Float, val timelineDurationMs: Long)

data class OverlayAudio(val inputIndex: Int, val volume: Float, val delayMs: Long)

data class AudioFormatSpec(val sampleRateHz: Int, val channels: Int)

/**
 * Everything the renderer needs, independent of FFmpeg syntax. Inputs are the video clips in order, followed by the
 * overlay audio clips. [originalAudio] and [audioFormat] are null when there is no such sound. [durationMs] is the
 * length of the video; audio running past it is cut.
 */
data class RenderPlan(
    val canvas: Canvas,
    val inputs: List<RenderInput>,
    val videoSegments: List<VideoSegment>,
    val originalAudio: List<OriginalAudioSegment>?,
    val overlays: List<OverlayAudio>,
    val audioFormat: AudioFormatSpec?,
    val durationMs: Long,
)

sealed interface RenderError {
    data object NoVideo : RenderError
    data class MissingSourceInfo(val source: MediaSource) : RenderError
    /** Rendering reads plain files; content URIs must be staged first. */
    data class SourceNotStaged(val source: MediaSource) : RenderError
}

sealed interface RenderPlanResult {
    data class Success(val plan: RenderPlan) : RenderPlanResult
    data class Failure(val error: RenderError) : RenderPlanResult
}
