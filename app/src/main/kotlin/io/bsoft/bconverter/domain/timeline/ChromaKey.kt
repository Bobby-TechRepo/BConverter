package io.bsoft.bconverter.domain.timeline

import kotlin.math.hypot

/**
 * Makes one colour of an overlay clip transparent (a "green screen"). [similarity] is how far from [rgb] (in colour, not brightness) a
 * pixel may be and still disappear; [blend] is the soft edge beyond it. Both are on FFmpeg's `chromakey` scale.
 */
data class ChromaKey(val rgb: Int = 0x00FF00, val similarity: Float = DEFAULT_SIMILARITY, val blend: Float = DEFAULT_BLEND) {
    init {
        require(rgb in 0..0xFFFFFF) { "colour" }
        require(similarity.isFinite() && similarity in MIN_SIMILARITY..MAX_SIMILARITY) { "similarity must be within $MIN_SIMILARITY..$MAX_SIMILARITY" }
        require(blend.isFinite() && blend in 0f..MAX_BLEND) { "blend must be within 0..$MAX_BLEND" }
    }

    companion object {
        const val MIN_SIMILARITY = 0.01f
        const val MAX_SIMILARITY = 0.5f
        const val MAX_BLEND = 0.5f
        const val DEFAULT_SIMILARITY = 0.2f
        const val DEFAULT_BLEND = 0.1f
    }
}

/**
 * The same arithmetic as FFmpeg's `chromakey` filter (measured against it): the key is turned into full-range U/V, each pixel into
 * limited-range U/V, and the distance between them, scaled by 255·√2, is compared with the similarity. Used to show the keyed frame
 * in the editor; the export uses FFmpeg itself.
 */
object ChromaKeyMath {
    private const val SCALE = 360.62445f // 255 * sqrt(2)

    /** How opaque a pixel stays, 0 (removed) to 1 (kept). */
    fun alpha(r: Int, g: Int, b: Int, key: ChromaKey): Float {
        val kr = (key.rgb shr 16) and 0xFF; val kg = (key.rgb shr 8) and 0xFF; val kb = key.rgb and 0xFF
        val ku = 128f + (-0.169f * kr - 0.331f * kg + 0.5f * kb)
        val kv = 128f + (0.5f * kr - 0.419f * kg - 0.081f * kb)
        val u = 128f + (-0.148f * r - 0.291f * g + 0.439f * b)
        val v = 128f + (0.439f * r - 0.368f * g - 0.071f * b)
        val diff = hypot(u - ku, v - kv) / SCALE
        return if (key.blend > 0.0001f) ((diff - key.similarity) / key.blend).coerceIn(0f, 1f) else if (diff > key.similarity) 1f else 0f
    }

    /** [argb] pixels with the alpha of each multiplied by [alpha] (other channels untouched). */
    fun apply(argb: IntArray, key: ChromaKey): IntArray = IntArray(argb.size) { i ->
        val p = argb[i]
        val a = ((p ushr 24) * alpha((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF, key)).let { Math.round(it) }.coerceIn(0, 255)
        (a shl 24) or (p and 0xFFFFFF)
    }
}
