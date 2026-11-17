package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.timeline.JoinEffect
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.TransitionType
import kotlin.math.roundToLong

/** Where a clip is drawn on its lane, in pixels (or dp — whatever unit the scale was built in). */
data class ClipBlock(val id: String, val xPx: Float, val widthPx: Float)

/** The tappable marker on the cut before video clip [clipId]; it spans the transition (at least [TimelineScale.MIN_MARKER_WIDTH_PX]). */
data class JoinMarker(val clipIndex: Int, val clipId: String, val centerPx: Float, val widthPx: Float, val effect: JoinEffect)

/** The clip block under [xPx], or null. Blocks are half-open: a boundary belongs to the later block. */
fun List<ClipBlock>.hit(xPx: Float): ClipBlock? = firstOrNull { xPx >= it.xPx && xPx < it.xPx + it.widthPx }

/**
 * Pure timeline geometry (spec §21): the zoom level and every conversion between time and horizontal position.
 * No Compose or Android here, so it is unit-tested.
 */
class TimelineScale(val pxPerSecond: Float) {
    init { require(pxPerSecond.isFinite() && pxPerSecond in MIN_PX_PER_SECOND..MAX_PX_PER_SECOND) { "scale out of range: $pxPerSecond" } }

    fun xOf(ms: Long): Float = ms * pxPerSecond / 1000f
    fun widthOf(durationMs: Long): Float = xOf(durationMs)
    fun msAt(xPx: Float): Long = (xPx / pxPerSecond * 1000f).roundToLong().coerceAtLeast(0)

    /** Ignores factors that are not positive finite numbers; the result stays within the zoom limits. */
    fun zoomedBy(factor: Float): TimelineScale =
        if (!factor.isFinite() || factor <= 0f) this else TimelineScale((pxPerSecond * factor).coerceIn(MIN_PX_PER_SECOND, MAX_PX_PER_SECOND))

    /** The smallest round step (in ms) that keeps ruler labels at least [MIN_LABEL_SPACING_PX] apart. */
    fun rulerStepMs(): Long = RULER_STEPS_S.firstOrNull { it * pxPerSecond >= MIN_LABEL_SPACING_PX }?.times(1000L) ?: (RULER_STEPS_S.last() * 1000L)

    fun ticks(durationMs: Long): List<Long> {
        val step = rulerStepMs()
        return generateSequence(0L) { it + step }.takeWhile { it <= durationMs.coerceAtLeast(0) }.toList()
    }

    fun videoBlocks(timeline: Timeline): List<ClipBlock> =
        timeline.videoClips.map { ClipBlock(it.id, xOf(it.startTimeMs), widthOf(it.durationMs).coerceAtLeast(MIN_BLOCK_WIDTH_PX)) }

    /** One marker per cut: centred on the cut, or on the overlap of a crossfade; wider the longer the transition. */
    fun joinMarkers(timeline: Timeline): List<JoinMarker> = (1 until timeline.videoClips.size).map { i ->
        val clip = timeline.videoClips[i]
        val effect = timeline.joinBefore(i)
        val start = xOf(clip.startTimeMs)
        val center = if (effect.type == TransitionType.CROSSFADE) start + xOf(effect.durationMs) / 2f else start
        JoinMarker(i, clip.id, center, maxOf(MIN_MARKER_WIDTH_PX, widthOf(effect.durationMs)), effect)
    }

    fun audioLanes(timeline: Timeline): List<List<ClipBlock>> =
        timeline.audioTracks.map { track ->
            track.clips.sortedBy { it.startTimeMs }.map { ClipBlock(it.id, xOf(it.startTimeMs), widthOf(it.durationMs).coerceAtLeast(MIN_BLOCK_WIDTH_PX)) }
        }

    /** Cue blocks of a subtitle track, as shown (offset applied). Ids are the cue ids. */
    fun subtitleLane(track: io.bsoft.bconverter.domain.subtitle.SubtitleTrack): List<ClipBlock> =
        track.effectiveCues().map { ClipBlock(it.id, xOf(it.startMs), widthOf(it.durationMs).coerceAtLeast(MIN_BLOCK_WIDTH_PX)) }

    /** The block of a text overlay on the timeline (its id is the overlay id). */
    fun overlayBlock(o: io.bsoft.bconverter.domain.overlay.TextOverlay): ClipBlock = ClipBlock(o.id, xOf(o.startMs), widthOf(o.durationMs).coerceAtLeast(MIN_BLOCK_WIDTH_PX))

    fun pipBlock(c: io.bsoft.bconverter.domain.timeline.PipClip): ClipBlock = ClipBlock(c.id, xOf(c.startTimeMs), widthOf(c.durationMs).coerceAtLeast(MIN_BLOCK_WIDTH_PX))

    /** Wide enough to scroll to the end of the longest lane. */
    /** Where keyframes at [offsetsMs] fall inside [block] (a clip of [durationMs]), as distances from its left edge, kept inside the block. */
    fun keyframeXs(block: ClipBlock, offsetsMs: List<Long>, durationMs: Long): List<Float> =
        offsetsMs.map { if (durationMs <= 0L) 0f else (xOf(it.coerceIn(0L, durationMs))).coerceIn(0f, block.widthPx) }.distinct()

    fun contentWidthPx(timeline: Timeline): Float = widthOf(timeline.durationMs)

    companion object {
        const val MIN_PX_PER_SECOND = 0.5f
        const val MAX_PX_PER_SECOND = 600f
        const val DEFAULT_PX_PER_SECOND = 60f
        const val MIN_LABEL_SPACING_PX = 64f
        const val MIN_BLOCK_WIDTH_PX = 8f
        const val MIN_MARKER_WIDTH_PX = 28f
        private val RULER_STEPS_S = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1_800, 3_600)

        /** The scale at which the whole timeline just fits [viewportPx]. */
        fun fitting(durationMs: Long, viewportPx: Float): TimelineScale {
            if (durationMs <= 0 || !viewportPx.isFinite() || viewportPx <= 0f) return TimelineScale(DEFAULT_PX_PER_SECOND)
            return TimelineScale((viewportPx / (durationMs / 1000f)).coerceIn(MIN_PX_PER_SECOND, MAX_PX_PER_SECOND))
        }
    }
}
