package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.FilterParam
import io.bsoft.bconverter.domain.filter.ToneMode
import org.json.JSONObject

/** JSON form of a [ClipFilter]: only values that differ from the identity are written, so unfiltered clips cost nothing. */
internal object ClipFilterJson {
    fun encode(f: ClipFilter): JSONObject = JSONObject().also { o ->
        for (p in FilterParam.entries) {
            val v = p.get(f)
            if (v != p.default) o.put(p.key, v.toDouble())
        }
        if (f.tone != ToneMode.NONE) o.put("tone", f.tone.name)
        if (f.invert) o.put("invert", true)
        f.presetId?.let { o.put("preset", it) }
    }

    /** Lenient: unknown keys are ignored, out-of-range numbers are clamped and an unknown tone means none. */
    fun decode(o: JSONObject): ClipFilter {
        var f = ClipFilter.None
        for (p in FilterParam.entries) if (o.has(p.key)) {
            val v = o.optDouble(p.key, p.default.toDouble())
            if (v.isFinite()) f = p.set(f, v.toFloat())
        }
        return f.copy(
            tone = ToneMode.entries.firstOrNull { it.name == o.optString("tone") } ?: ToneMode.NONE,
            invert = o.optBoolean("invert", false),
            presetId = o.optString("preset").ifEmpty { null },
        )
    }
}
