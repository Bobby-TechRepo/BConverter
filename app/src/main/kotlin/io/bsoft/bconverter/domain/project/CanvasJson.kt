package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.canvas.CanvasAspect
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings
import org.json.JSONObject

/** Saves the canvas settings; reading is lenient (an unknown name or a bad value falls back to the default for that part). */
internal object CanvasJson {
    fun encode(c: CanvasSettings): JSONObject = JSONObject()
        .put("aspect", c.aspect.name)
        .put("fit", c.fit.name)
        .put("background", when (val b = c.background) { CanvasBackground.Blur -> "blur"; is CanvasBackground.Color -> b.rgb })

    fun decode(o: JSONObject): CanvasSettings {
        val defaults = CanvasSettings()
        return CanvasSettings(
            aspect = runCatching { CanvasAspect.valueOf(o.getString("aspect")) }.getOrDefault(defaults.aspect),
            fit = runCatching { CanvasFit.valueOf(o.getString("fit")) }.getOrDefault(defaults.fit),
            background = o.optString("background", "").let { raw ->
                if (raw == "blur") CanvasBackground.Blur
                else raw.toLongOrNull()?.let { CanvasBackground.Color(it.coerceIn(0L, 0xFFFFFFL).toInt()) } ?: defaults.background
            },
        )
    }
}
