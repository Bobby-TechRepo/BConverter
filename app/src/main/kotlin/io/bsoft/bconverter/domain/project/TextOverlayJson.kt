package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayBackground
import io.bsoft.bconverter.domain.overlay.OverlayEasing
import io.bsoft.bconverter.domain.overlay.OverlayGlow
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.OverlayKeyframe
import io.bsoft.bconverter.domain.overlay.OverlayKeyframes
import io.bsoft.bconverter.domain.overlay.OverlayOutline
import io.bsoft.bconverter.domain.overlay.OverlayShadow
import io.bsoft.bconverter.domain.overlay.OverlayTextAlign
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.overlay.TextOverlayStyle
import org.json.JSONObject

/** JSON form of a [TextOverlay]. Fields at their default are omitted, so files stay small and new fields can be added later. */
internal object TextOverlayJson {
    private val defaultStyle = TextOverlayStyle()

    fun encode(t: TextOverlay): JSONObject = JSONObject()
        .put("id", t.id).put("text", t.text).put("startMs", t.startMs).put("endMs", t.endMs)
        .put("x", t.centerX.toDouble()).put("y", t.centerY.toDouble())
        .also { o ->
            t.name?.let { o.put("name", it) }
            if (t.rotationDeg != 0f) o.put("rotation", t.rotationDeg.toDouble())
            if (t.scale != 1f) o.put("scale", t.scale.toDouble())
            t.widthFraction?.let { o.put("width", it.toDouble()) }
            o.put("style", style(t.style))
            t.animIn?.let { o.put("animIn", anim(it)) }
            t.animOut?.let { o.put("animOut", anim(it)) }
            t.image?.let { o.put("image", image(it)) }
            if (t.keyframes.isNotEmpty()) o.put("keys", org.json.JSONArray().also { a -> t.keyframes.forEach { k -> a.put(JSONObject().put("t", k.offsetMs).put("x", k.x.toDouble()).put("y", k.y.toDouble()).put("z", k.zoom.toDouble()).put("o", k.opacity.toDouble()).also { j -> if (k.eased) j.put("e", true); if (k.rotationDeg != 0f) j.put("r", k.rotationDeg.toDouble()) }) } })
        }

    fun decode(o: JSONObject): TextOverlay = TextOverlay(
        id = o.getString("id"), text = o.getString("text"), startMs = o.getLong("startMs"), endMs = o.getLong("endMs"),
        centerX = o.getDouble("x").toFloat(), centerY = o.getDouble("y").toFloat(),
        rotationDeg = o.optDouble("rotation", 0.0).toFloat(), scale = o.optDouble("scale", 1.0).toFloat(),
        widthFraction = if (o.has("width")) o.getDouble("width").toFloat() else null,
        style = o.optJSONObject("style")?.let { style(it) } ?: defaultStyle,
        animIn = o.optJSONObject("animIn")?.let { anim(it) }, animOut = o.optJSONObject("animOut")?.let { anim(it) },
        name = o.optString("name").ifEmpty { null },
        image = o.optJSONObject("image")?.let { image(it) },
        keyframes = keyframes(o),
    )

    /** Lenient: a bad keyframe is skipped, the rest are sorted and made unique, and no more than the limit are kept. */
    private fun keyframes(o: JSONObject): List<OverlayKeyframe> {
        val a = o.optJSONArray("keys") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val k = a.optJSONObject(i) ?: return@mapNotNull null
            runCatching { OverlayKeyframe(k.optLong("t", 0).coerceAtLeast(0), k.optDouble("x", 0.5).toFloat(), k.optDouble("y", 0.5).toFloat(), k.optDouble("z", 1.0).toFloat(), k.optDouble("o", 1.0).toFloat(), k.optBoolean("e", false), k.optDouble("r", 0.0).toFloat()) }.getOrNull()
        }.sortedBy { it.offsetMs }.distinctBy { it.offsetMs }.take(OverlayKeyframes.MAX)
    }

    private fun image(i: OverlayImage) = JSONObject().put("path", i.path).put("w", i.widthPx).put("h", i.heightPx)
        .also { if (i.heightFraction != DEFAULT_IMAGE_HEIGHT) it.put("height", i.heightFraction.toDouble()) }
    private fun image(o: JSONObject) = OverlayImage(o.getString("path"), o.getInt("w"), o.getInt("h"), o.optDouble("height", DEFAULT_IMAGE_HEIGHT.toDouble()).toFloat())
    private const val DEFAULT_IMAGE_HEIGHT = 0.3f

    private fun anim(a: OverlayAnimation) = JSONObject().put("kind", a.kind.id).put("durationMs", a.durationMs).put("easing", a.easing.name)
    private fun anim(o: JSONObject) = OverlayAnimation(AnimationKind(o.getString("kind")), o.getLong("durationMs"), OverlayEasing.valueOf(o.getString("easing")))

    private fun style(s: TextOverlayStyle): JSONObject = JSONObject()
        .put("weight", s.fontWeight).put("italic", s.italic).put("underline", s.underline).put("size", s.sizeFraction.toDouble())
        .put("color", s.color).put("opacity", s.opacity.toDouble()).put("align", s.align.name)
        .put("letterSpacing", s.letterSpacing.toDouble()).put("lineSpacing", s.lineSpacing.toDouble())
        .also { o ->
            s.fontFamily?.let { o.put("font", it) }
            s.outline?.let { o.put("outline", JSONObject().put("color", it.color).put("width", it.widthFraction.toDouble())) }
            s.shadow?.let { o.put("shadow", JSONObject().put("color", it.color).put("dx", it.dx.toDouble()).put("dy", it.dy.toDouble()).put("blur", it.blur.toDouble())) }
            s.glow?.let { o.put("glow", JSONObject().put("color", it.color).put("radius", it.radius.toDouble())) }
            s.background?.let { o.put("background", JSONObject().put("color", it.color).put("padding", it.padding.toDouble()).put("corner", it.cornerRadius.toDouble())) }
        }

    private fun style(o: JSONObject) = TextOverlayStyle(
        fontFamily = o.optString("font").ifEmpty { null },
        fontWeight = o.optInt("weight", 400), italic = o.optBoolean("italic", false), underline = o.optBoolean("underline", false),
        sizeFraction = o.optDouble("size", defaultStyle.sizeFraction.toDouble()).toFloat(),
        color = o.optInt("color", defaultStyle.color), opacity = o.optDouble("opacity", 1.0).toFloat(),
        align = o.optString("align").ifEmpty { null }?.let { OverlayTextAlign.valueOf(it) } ?: OverlayTextAlign.CENTER,
        letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(), lineSpacing = o.optDouble("lineSpacing", 1.0).toFloat(),
        outline = o.optJSONObject("outline")?.let { OverlayOutline(it.getInt("color"), it.getDouble("width").toFloat()) },
        shadow = o.optJSONObject("shadow")?.let { OverlayShadow(it.getInt("color"), it.getDouble("dx").toFloat(), it.getDouble("dy").toFloat(), it.getDouble("blur").toFloat()) },
        glow = o.optJSONObject("glow")?.let { OverlayGlow(it.getInt("color"), it.getDouble("radius").toFloat()) },
        background = o.optJSONObject("background")?.let { OverlayBackground(it.getInt("color"), it.getDouble("padding").toFloat(), it.getDouble("corner").toFloat()) },
    )
}
