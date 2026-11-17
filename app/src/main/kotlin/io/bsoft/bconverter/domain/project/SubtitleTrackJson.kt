package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.subtitle.SubtitleAlignment
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleExportMode
import io.bsoft.bconverter.domain.subtitle.SubtitleFormat
import io.bsoft.bconverter.domain.subtitle.SubtitleStyle
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject

/** JSON form of a [SubtitleTrack] inside a project file. Decoding throws on bad data; the project decoder maps that to Corrupt. */
internal object SubtitleTrackJson {
    fun encode(t: SubtitleTrack): JSONObject = JSONObject()
        .put("id", t.id).put("name", t.name)
        .put("cues", JSONArray().also { a -> t.cues.forEach { a.put(cue(it)) } })
        .also { o ->
            if (t.styles.isNotEmpty()) o.put("styles", JSONArray().also { a -> t.styles.values.forEach { a.put(style(it)) } })
            t.language?.let { o.put("language", it) }
            t.title?.let { o.put("title", it) }
            if (t.offsetMs != 0L) o.put("offsetMs", t.offsetMs)
            t.sourceFormat?.let { o.put("format", it.name) }
            t.playResX?.let { o.put("playResX", it) }
            t.playResY?.let { o.put("playResY", it) }
            if (t.isDefault) o.put("default", true)
            if (t.isForced) o.put("forced", true)
            if (t.exportMode != SubtitleExportMode.SOFT) o.put("exportMode", t.exportMode.name)
        }

    fun decode(o: JSONObject): SubtitleTrack {
        val styles = o.optJSONArray("styles")?.let { a -> (0 until a.length()).map { style(a.getJSONObject(it)) } } ?: emptyList()
        return SubtitleTrack(
            id = o.getString("id"), name = o.getString("name"),
            cues = o.getJSONArray("cues").let { a -> (0 until a.length()).map { cue(a.getJSONObject(it)) } },
            styles = styles.associateBy { it.name },
            language = o.optString("language").ifEmpty { null }, title = o.optString("title").ifEmpty { null },
            offsetMs = o.optLong("offsetMs", 0),
            sourceFormat = o.optString("format").ifEmpty { null }?.let { SubtitleFormat.valueOf(it) },
            playResX = if (o.has("playResX")) o.getInt("playResX") else null,
            playResY = if (o.has("playResY")) o.getInt("playResY") else null,
            isDefault = o.optBoolean("default", false), isForced = o.optBoolean("forced", false),
            exportMode = o.optString("exportMode").ifEmpty { null }?.let { SubtitleExportMode.valueOf(it) } ?: SubtitleExportMode.SOFT,
        )
    }

    private fun cue(c: SubtitleCue) = JSONObject()
        .put("id", c.id).put("startMs", c.startMs).put("endMs", c.endMs).put("text", c.text)
        .also { o -> c.styleName?.let { o.put("style", it) }; c.assOverride?.let { o.put("override", it) } }

    private fun cue(o: JSONObject) = SubtitleCue(
        o.getString("id"), o.getLong("startMs"), o.getLong("endMs"), o.getString("text"),
        o.optString("style").ifEmpty { null }, o.optString("override").ifEmpty { null },
    )

    private fun style(s: SubtitleStyle) = JSONObject()
        .put("name", s.name).put("fontSize", s.fontSize.toDouble()).put("bold", s.bold).put("italic", s.italic).put("underline", s.underline)
        .put("primary", s.primaryColor).put("outline", s.outlineColor).put("back", s.backColor).put("boxed", s.boxed)
        .put("outlineWidth", s.outlineWidth.toDouble()).put("shadow", s.shadow.toDouble()).put("alignment", s.alignment.numpad)
        .put("marginL", s.marginL).put("marginR", s.marginR).put("marginV", s.marginV)
        .also { o -> s.fontFamily?.let { o.put("font", it) } }

    private fun style(o: JSONObject) = SubtitleStyle(
        name = o.getString("name"), fontFamily = o.optString("font").ifEmpty { null }, fontSize = o.getDouble("fontSize").toFloat(),
        bold = o.getBoolean("bold"), italic = o.getBoolean("italic"), underline = o.getBoolean("underline"),
        primaryColor = o.getInt("primary"), outlineColor = o.getInt("outline"), backColor = o.getInt("back"), boxed = o.getBoolean("boxed"),
        outlineWidth = o.getDouble("outlineWidth").toFloat(), shadow = o.getDouble("shadow").toFloat(),
        alignment = SubtitleAlignment.fromNumpad(o.getInt("alignment")) ?: throw IllegalArgumentException("bad alignment"),
        marginL = o.getInt("marginL"), marginR = o.getInt("marginR"), marginV = o.getInt("marginV"),
    )
}
