package io.bsoft.bconverter.domain.overlay

enum class OverlayTextAlign { LEFT, CENTER, RIGHT }

/** Outline around the glyphs. [widthFraction] is a fraction of the font size, so it scales with the text. */
data class OverlayOutline(val color: Int = 0xFF000000.toInt(), val widthFraction: Float = 0.06f) {
    init { require(widthFraction.isFinite() && widthFraction in 0f..0.5f) { "outline width $widthFraction" } }
}

/** Drop shadow. Offsets and blur are fractions of the font size. */
data class OverlayShadow(val color: Int = 0x99000000.toInt(), val dx: Float = 0.05f, val dy: Float = 0.05f, val blur: Float = 0.05f) {
    init { require(dx.isFinite() && dy.isFinite() && blur.isFinite() && blur >= 0f && kotlin.math.abs(dx) <= 2f && kotlin.math.abs(dy) <= 2f && blur <= 2f) { "shadow" } }
}

/** Soft glow around the glyphs; [radius] is a fraction of the font size. */
data class OverlayGlow(val color: Int = 0xFFFFFFFF.toInt(), val radius: Float = 0.2f) {
    init { require(radius.isFinite() && radius in 0f..2f) { "glow radius $radius" } }
}

/** Box behind the text. [padding] and [cornerRadius] are fractions of the font size. */
data class OverlayBackground(val color: Int = 0x99000000.toInt(), val padding: Float = 0.25f, val cornerRadius: Float = 0.2f) {
    init { require(padding.isFinite() && padding in 0f..2f && cornerRadius.isFinite() && cornerRadius in 0f..2f) { "background" } }
}

/**
 * Everything about how overlay text looks, in units that do not depend on the video's resolution:
 * [sizeFraction] is the font size as a fraction of the frame height; spacing values are relative to the font size.
 */
data class TextOverlayStyle(
    val fontFamily: String? = null,
    /** CSS-style weight 100..900; 400 normal, 700 bold. */
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val sizeFraction: Float = 0.06f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 1f,
    val align: OverlayTextAlign = OverlayTextAlign.CENTER,
    /** Extra space between letters in em (0 = none). */
    val letterSpacing: Float = 0f,
    /** Line height multiplier (1 = the font's natural line height). */
    val lineSpacing: Float = 1f,
    val outline: OverlayOutline? = null,
    val shadow: OverlayShadow? = null,
    val glow: OverlayGlow? = null,
    val background: OverlayBackground? = null,
) {
    init {
        require(fontWeight in 100..900) { "fontWeight $fontWeight" }
        require(sizeFraction.isFinite() && sizeFraction > 0f && sizeFraction <= 1f) { "sizeFraction $sizeFraction" }
        require(opacity.isFinite() && opacity in 0f..1f) { "opacity $opacity" }
        require(letterSpacing.isFinite() && letterSpacing in -0.5f..5f) { "letterSpacing $letterSpacing" }
        require(lineSpacing.isFinite() && lineSpacing > 0f && lineSpacing <= 5f) { "lineSpacing $lineSpacing" }
    }
}

/** How an animation moves the overlay. Identified by [id] so new kinds can be added (and unknown ones kept) without changing the model. */
data class AnimationKind(val id: String) {
    companion object {
        val FADE = AnimationKind("fade")
        val SLIDE_LEFT = AnimationKind("slide_left")
        val SLIDE_RIGHT = AnimationKind("slide_right")
        val SLIDE_UP = AnimationKind("slide_up")
        val SLIDE_DOWN = AnimationKind("slide_down")
        val SCALE = AnimationKind("scale")
        val builtIn = listOf(FADE, SLIDE_LEFT, SLIDE_RIGHT, SLIDE_UP, SLIDE_DOWN, SCALE)
    }
}

enum class OverlayEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

/** An in or out animation lasting [durationMs]; used as `animIn` it plays at the start, as `animOut` it plays at the end. */
data class OverlayAnimation(val kind: AnimationKind, val durationMs: Long = 500, val easing: OverlayEasing = OverlayEasing.EASE_OUT) {
    init { require(durationMs > 0) { "animation duration $durationMs" } }
}

/**
 * A picture placed on the video. [path] is a file in the app's own storage (a copy of what the person picked);
 * [widthPx]/[heightPx] are its pixel size, so its shape is known without opening it. At scale 1 the picture is
 * [heightFraction] of the frame height tall, whatever the frame size; its width follows its aspect ratio.
 */
data class OverlayImage(val path: String, val widthPx: Int, val heightPx: Int, val heightFraction: Float = 0.3f) {
    init {
        require(path.isNotBlank()) { "blank image path" }
        require(widthPx > 0 && heightPx > 0) { "image size ${widthPx}x$heightPx" }
        require(heightFraction.isFinite() && heightFraction > 0f && heightFraction <= 1f) { "heightFraction $heightFraction" }
    }
    val aspect: Float get() = widthPx.toFloat() / heightPx
}

/**
 * Something shown on the picture from [startMs] to [endMs] on the project timeline: text, or (when [image] is set) a picture.
 * Both share position, rotation, scale, opacity, timing, animations and one layer order, which is why they are one type
 * ("overlay"); for a picture [text] is empty and the text-only style fields are ignored (only [TextOverlayStyle.opacity] counts).
 *
 * Position is the text box centre as a fraction of the frame (0,0 top-left; 1,1 bottom-right), so it is the same at any resolution.
 * [rotationDeg] turns it clockwise about its centre; [scale] enlarges it about its centre; [widthFraction] is the wrapping width
 * as a fraction of the frame width (null = no wrapping). Overlays are stacked in list order: the last one is on top.
 */
data class TextOverlay(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val rotationDeg: Float = 0f,
    val scale: Float = 1f,
    val widthFraction: Float? = null,
    val style: TextOverlayStyle = TextOverlayStyle(),
    val animIn: OverlayAnimation? = null,
    val animOut: OverlayAnimation? = null,
    val name: String? = null,
    /** Set for a picture overlay. */
    val image: OverlayImage? = null,
    /** An animation through several looks; when not empty it replaces the centre (and adds a zoom and an opacity) over time. */
    val keyframes: List<OverlayKeyframe> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "blank id" }
        require(startMs >= 0 && endMs > startMs) { "bad timing $startMs..$endMs" }
        require(centerX.isFinite() && centerY.isFinite() && rotationDeg.isFinite()) { "non-finite position" }
        require(scale.isFinite() && scale > 0f && scale <= 20f) { "scale $scale" }
        require(OverlayKeyframes.isOrdered(keyframes)) { "keyframes must be in increasing order" }
        require(widthFraction == null || (widthFraction.isFinite() && widthFraction > 0f && widthFraction <= 1f)) { "widthFraction $widthFraction" }
    }

    val isImage: Boolean get() = image != null
    val durationMs: Long get() = endMs - startMs
    fun isShownAt(timeMs: Long): Boolean = timeMs in startMs until endMs
}

/** Pixel measurements of an overlay for one concrete frame size; the same overlay gives proportional results at any size. */
data class OverlayPixels(val centerX: Float, val centerY: Float, val fontSizePx: Float, val boxWidthPx: Float?, val rotationDeg: Float)

object OverlayGeometry {
    /** (width, height) in pixels of a picture overlay at [scale] for one frame size; the aspect ratio is the picture's own. */
    fun imageSizePx(o: TextOverlay, frameWidth: Int, frameHeight: Int): Pair<Float, Float> {
        val img = requireNotNull(o.image) { "not a picture overlay" }
        require(frameWidth > 0 && frameHeight > 0) { "frame size" }
        val h = img.heightFraction * frameHeight * o.scale
        return (h * img.aspect) to h
    }

    fun toPixels(o: TextOverlay, frameWidth: Int, frameHeight: Int): OverlayPixels {
        require(frameWidth > 0 && frameHeight > 0) { "frame size" }
        return OverlayPixels(
            centerX = o.centerX * frameWidth, centerY = o.centerY * frameHeight,
            fontSizePx = o.style.sizeFraction * frameHeight * o.scale,
            boxWidthPx = o.widthFraction?.let { it * frameWidth * o.scale },
            rotationDeg = o.rotationDeg,
        )
    }
}
