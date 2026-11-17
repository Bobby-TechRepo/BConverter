package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.JoinEffect
import java.io.File

/** The output picture: every clip is scaled/padded to this size and conformed to this frame rate. */
data class Canvas(val width: Int, val height: Int, val frameRate: Double)

/** One FFmpeg input: [durationMs] of [file] starting at [seekMs] (source time). */
data class RenderInput(val file: File, val seekMs: Long, val durationMs: Long)

/**
 * One video clip in the output. [startMs]/[durationMs] are where it sits on the timeline; [join] is what happens at the cut before it
 * (a crossfade overlaps the previous clip, a fade through black darkens both sides of the cut).
 */
data class VideoSegment(
    val inputIndex: Int, val speed: Float,
    val durationMs: Long = 0, val startMs: Long = 0, val join: JoinEffect = JoinEffect.None,
    val filter: ClipFilter = ClipFilter.None,
    /** Crop, rotation and mirroring; [sourceWidth] × [sourceHeight] is the upright source picture the crop is measured in. */
    val transform: ClipTransform = ClipTransform.None,
    val sourceWidth: Int = 0, val sourceHeight: Int = 0,
    val reversed: Boolean = false,
    /** A freeze frame: the first picture of the input, held for [durationMs]. */
    val freeze: Boolean = false,
    /** A slow zoom or pan over the clip's picture (as it looks after [transform]). */
    val kenBurns: io.bsoft.bconverter.domain.transform.KenBurns = io.bsoft.bconverter.domain.transform.KenBurns.None,
    /** How long the clip lasts in its input, before any speed change. */
    val sourceMs: Long = 0,
)

/** The original sound of one video clip; [inputIndex] is null when the clip has no audio (silence is generated). */
data class OriginalAudioSegment(val inputIndex: Int?, val speed: Float, val timelineDurationMs: Long, val volume: Float = 1f, val fadeInMs: Long = 0, val fadeOutMs: Long = 0, val reversed: Boolean = false)

data class OverlayAudio(
    val inputIndex: Int, val volume: Float, val delayMs: Long, val speed: Float = 1f,
    val fadeInMs: Long = 0, val fadeOutMs: Long = 0, val durationMs: Long = 0,
)

/** A picture-in-picture clip in the output: [widthPx] wide, centred at the fractions [centerX]/[centerY] of the canvas, shown from [startMs] for [durationMs]. */
data class PipSegment(
    val inputIndex: Int, val startMs: Long, val durationMs: Long,
    val widthPx: Int, val centerX: Float, val centerY: Float, val opacity: Float,
    /** Picture fades at the two ends of its time, in ms (already fitted to the clip). */
    val fadeInMs: Long = 0, val fadeOutMs: Long = 0,
    /** A frame of this many pixels around the clip (0 = none), in 0xRRGGBB. The frame is added to [widthPx]. */
    val borderPx: Int = 0, val borderRgb: Int = 0xFFFFFF,
    /** A colour removed from the clip (before the frame is added), or null. */
    val chromaKey: io.bsoft.bconverter.domain.timeline.ChromaKey? = null,
    /** Playback speed of the clip; [durationMs] is already the length on the timeline. */
    val speed: Float = 1f,
    /** The outline; a shaped picture has no border ([borderPx] is 0). */
    val shape: io.bsoft.bconverter.domain.timeline.PipShape = io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE,
    /** When the clip grows or shrinks while it glides: its width at the end, in pixels (even). */
    val endWidthPx: Int? = null,
    /** An animation through several looks (replaces the position, size and opacity above; the first look is the start). */
    val keyframes: List<io.bsoft.bconverter.domain.timeline.PipKeyframe> = emptyList(),
    /** The centre glides to this point over [durationMs] (see [io.bsoft.bconverter.domain.timeline.PipMotion]), or stays. */
    val motion: io.bsoft.bconverter.domain.timeline.PipMotion? = null,
)

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
    /** Drawn over the finished video, first to last (the last is on top); their inputs follow the overlay audio inputs. */
    val pips: List<PipSegment> = emptyList(),
    /** How a clip that does not match the canvas is placed, and what shows in the bars. */
    val fit: io.bsoft.bconverter.domain.canvas.CanvasFit = io.bsoft.bconverter.domain.canvas.CanvasFit.FIT,
    val background: io.bsoft.bconverter.domain.canvas.CanvasBackground = io.bsoft.bconverter.domain.canvas.CanvasBackground.Color(0),
)

sealed interface RenderError {
    data object NoVideo : RenderError
    data class MissingSourceInfo(val source: MediaSource) : RenderError
    /** Rendering reads plain files; content URIs must be staged first. */
    data class SourceNotStaged(val source: MediaSource) : RenderError
    /** A reversed clip is longer than [ReversePolicy] allows at the export's picture size. */
    data class ReverseTooLong(val clipId: String, val maxDurationMs: Long) : RenderError
}

sealed interface RenderPlanResult {
    data class Success(val plan: RenderPlan) : RenderPlanResult
    data class Failure(val error: RenderError) : RenderPlanResult
}
