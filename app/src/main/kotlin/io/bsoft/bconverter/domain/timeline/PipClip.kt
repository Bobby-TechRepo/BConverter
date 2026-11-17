package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.model.MediaSource

/** Where a picture-in-picture clip sits on the frame and how it looks. Positions and sizes are fractions of the output picture. */
data class PipLayout(
    val centerX: Float = 0.75f,
    val centerY: Float = 0.25f,
    /** The clip's width as a share of the output width; its height follows from its own shape. */
    val width: Float = DEFAULT_WIDTH,
    val opacity: Float = 1f,
    /** A frame around the clip: its thickness as a share of the clip's own width (0 = none) and its colour, 0xRRGGBB. */
    val borderWidth: Float = 0f,
    val borderRgb: Int = 0xFFFFFF,
) {
    init {
        require(centerX.isFinite() && centerX in 0f..1f && centerY.isFinite() && centerY in 0f..1f) { "centre must be inside the frame" }
        require(width.isFinite() && width in MIN_WIDTH..MAX_WIDTH) { "width must be within $MIN_WIDTH..$MAX_WIDTH" }
        require(opacity.isFinite() && opacity in MIN_OPACITY..1f) { "opacity must be within $MIN_OPACITY..1" }
        require(borderWidth.isFinite() && borderWidth in 0f..MAX_BORDER) { "border must be within 0..$MAX_BORDER" }
        require(borderRgb in 0..0xFFFFFF) { "border colour" }
    }

    companion object {
        const val MIN_WIDTH = 0.1f
        const val MAX_WIDTH = 1f
        const val DEFAULT_WIDTH = 0.35f
        const val MIN_OPACITY = 0.1f
        const val MAX_BORDER = 0.1f
    }
}

/**
 * A second video shown over the main one for a while: [sourceStartMs]..[sourceEndMs] of its source, from [startTimeMs] on the
 * timeline, at normal speed. Its own sound is mixed in at [volume] (0 mutes it). Clips listed later are drawn on top.
 */
data class PipClip(
    val id: String,
    val source: MediaSource,
    val sourceDurationMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val startTimeMs: Long,
    val layout: PipLayout = PipLayout(),
    val volume: Float = 1f,
    /** The picture (and sound) fade in over the first and out over the last [fadeInMs]/[fadeOutMs]; together at most the clip's length. */
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    /** A colour made transparent, or null. */
    val chromaKey: ChromaKey? = null,
    /** Where the centre ends up: the clip glides from its layout position to this one over its whole length, or stays put (null). */
    val motion: PipMotion? = null,
    /** Playback speed, [VideoClip.MIN_SPEED]..[VideoClip.MAX_SPEED]; the clip lasts its source span divided by this on the timeline. */
    val speed: Float = 1f,
    /** The outline the picture is cut to. */
    val shape: PipShape = PipShape.RECTANGLE,
    /** An animation through several looks; when not empty it replaces the layout's position, size and opacity (and any glide). */
    val keyframes: List<PipKeyframe> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(sourceDurationMs > 0) { "sourceDurationMs must be > 0" }
        require(sourceStartMs >= 0 && sourceEndMs > sourceStartMs && sourceEndMs <= sourceDurationMs) { "source range must be inside the source" }
        require(startTimeMs >= 0) { "startTimeMs must be >= 0" }
        require(speed.isFinite() && speed in VideoClip.MIN_SPEED..VideoClip.MAX_SPEED) { "speed must be within ${VideoClip.MIN_SPEED}..${VideoClip.MAX_SPEED}" }
        require(PipKeyframes.isValid(keyframes, Long.MAX_VALUE)) { "keyframes must be in increasing order" }
        require(volume.isFinite() && volume in 0f..2f) { "volume must be within 0..2" }
        require(fadeInMs in 0..io.bsoft.bconverter.domain.audio.ClipSound.MAX_FADE_MS && fadeOutMs in 0..io.bsoft.bconverter.domain.audio.ClipSound.MAX_FADE_MS) { "fades must be within 0..10 s" }
    }

    val durationMs: Long get() = if (speed == 1f) sourceEndMs - sourceStartMs else Math.round((sourceEndMs - sourceStartMs) / speed.toDouble()).coerceAtLeast(1L)
    val endTimeMs: Long get() = startTimeMs + durationMs
}

/** The outline of an overlay clip. A rounded or circular picture has no border; a circle is cut from the middle of a square. */
enum class PipShape {
    RECTANGLE, ROUNDED, CIRCLE;
    companion object {
        /** Corner radius of [ROUNDED] as a share of the picture's shorter side. */
        const val ROUNDED_RADIUS = 0.15
    }
}

/** How visible a picture-in-picture clip is at a moment: its fades, as a factor 0..1 of its layout opacity. */
object PipFade {
    /** The fades that fit the clip (see [io.bsoft.bconverter.domain.audio.ClipSound.fit]). */
    fun fitted(clip: PipClip): Pair<Long, Long> = io.bsoft.bconverter.domain.audio.ClipSound.fit(clip.fadeInMs, clip.fadeOutMs, clip.durationMs)

    fun factor(clip: PipClip, timelineMs: Long): Float {
        val (fin, fout) = fitted(clip)
        val into = timelineMs - clip.startTimeMs
        val left = clip.endTimeMs - timelineMs
        val a = if (fin > 0 && into < fin) into.toFloat() / fin else 1f
        val b = if (fout > 0 && left < fout) left.toFloat() / fout else 1f
        return minOf(a, b).coerceIn(0f, 1f)
    }
}

/** A straight glide of a picture-in-picture clip's centre to ([endX], [endY]) (fractions of the frame); [eased] starts and ends gently. */
data class PipMotion(val endX: Float, val endY: Float, val eased: Boolean = true, val endWidth: Float? = null) {
    init {
        require(endX.isFinite() && endX in 0f..1f && endY.isFinite() && endY in 0f..1f) { "end must be inside the frame" }
        require(endWidth == null || (endWidth.isFinite() && endWidth in PipLayout.MIN_WIDTH..PipLayout.MAX_WIDTH)) { "end width must be within the size limits" }
    }
}

/** Where a clip's centre is at a moment, following its [PipMotion]. */
object PipPath {
    /** 0..1 along the glide at [timelineMs] (clamped), after easing. */
    fun progress(clip: PipClip, timelineMs: Long): Float {
        val raw = ((timelineMs - clip.startTimeMs).toFloat() / clip.durationMs).coerceIn(0f, 1f)
        return if (clip.motion?.eased == true) raw * raw * (3f - 2f * raw) else raw
    }

    /** The clip's width (a share of the frame) at a moment: its layout width, growing or shrinking to [PipMotion.endWidth] if it has one. */
    /** The look of the clip at a moment on the timeline: from its keyframes, or its layout and glide. */
    fun stateAt(clip: PipClip, timelineMs: Long): PipState {
        if (clip.keyframes.isNotEmpty()) return PipKeyframes.interpolate(clip.keyframes, timelineMs - clip.startTimeMs)
        val (x, y) = centerAt(clip, timelineMs)
        return PipState(x, y, widthAt(clip, timelineMs), clip.layout.opacity)
    }

    fun widthAt(clip: PipClip, timelineMs: Long): Float {
        val end = clip.motion?.endWidth ?: return clip.layout.width
        return clip.layout.width + (end - clip.layout.width) * progress(clip, timelineMs)
    }

    fun centerAt(clip: PipClip, timelineMs: Long): Pair<Float, Float> {
        val m = clip.motion ?: return clip.layout.centerX to clip.layout.centerY
        val p = progress(clip, timelineMs)
        return (clip.layout.centerX + (m.endX - clip.layout.centerX) * p) to (clip.layout.centerY + (m.endY - clip.layout.centerY) * p)
    }
}
