package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropRect
import io.bsoft.bconverter.domain.transform.KenBurns
import io.bsoft.bconverter.domain.transform.KenBurnsMode
import org.json.JSONObject

/** JSON form of a [ClipTransform]: only what differs from "whole picture, upright" is written. */
internal object ClipTransformJson {
    fun encode(t: ClipTransform): JSONObject = JSONObject().also { o ->
        if (!t.crop.isFull) o.put("crop", JSONObject().put("l", t.crop.left.toDouble()).put("t", t.crop.top.toDouble()).put("r", t.crop.right.toDouble()).put("b", t.crop.bottom.toDouble()))
        if (t.rotationDegrees != 0) o.put("rotation", t.rotationDegrees)
        if (t.flipHorizontal) o.put("flipH", true)
        if (t.flipVertical) o.put("flipV", true)
    }

    fun encodeMotion(k: KenBurns): JSONObject = JSONObject().put("mode", k.mode.name).put("amount", k.amount.toDouble())

    /** Lenient: an unknown mode means no motion, and an amount out of range is clamped. */
    fun decodeMotion(o: JSONObject): KenBurns {
        val mode = runCatching { KenBurnsMode.valueOf(o.optString("mode")) }.getOrDefault(KenBurnsMode.NONE)
        val amount = o.optDouble("amount", KenBurns.DEFAULT_AMOUNT.toDouble()).takeIf { it.isFinite() }?.toFloat()?.coerceIn(KenBurns.MIN_AMOUNT, KenBurns.MAX_AMOUNT) ?: KenBurns.DEFAULT_AMOUNT
        return KenBurns(mode, amount)
    }

    /** Lenient: a crop that is out of range is clamped, one that is too small or unreadable means no crop, and rotation snaps to a quarter turn. */
    fun decode(o: JSONObject): ClipTransform {
        val c = o.optJSONObject("crop")
        val crop = if (c == null) CropRect.Full else runCatching {
            fun v(k: String, d: Double) = c.optDouble(k, d).takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f) ?: d.toFloat()
            CropRect(v("l", 0.0), v("t", 0.0), v("r", 1.0), v("b", 1.0))
        }.getOrDefault(CropRect.Full)
        val turns = Math.floorMod(Math.round(o.optInt("rotation", 0) / 90.0).toInt(), 4)
        return ClipTransform(crop, turns * 90, o.optBoolean("flipH", false), o.optBoolean("flipV", false))
    }
}
