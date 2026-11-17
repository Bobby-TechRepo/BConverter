package io.bsoft.bconverter.domain.project

import android.net.Uri
import io.bsoft.bconverter.domain.audio.ClipSound
import io.bsoft.bconverter.domain.timeline.FreezeFrame
import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.ChromaKey
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.domain.timeline.PipMotion
import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.AudioTrack
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.Transition
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

sealed interface DecodeResult {
    data class Decoded(val stored: StoredProject) : DecodeResult
    data class Corrupt(val reason: String) : DecodeResult
}

/**
 * Versioned JSON form of a [StoredProject]. Decoding never throws: anything unreadable (not JSON, truncated, unknown
 * version, missing field, unknown enum value, clip that violates its own invariants) is [DecodeResult.Corrupt].
 */
object ProjectSerializer {
    const val VERSION = 1

    fun encode(stored: StoredProject): String {
        val p = stored.project
        return JSONObject()
            .put("version", VERSION)
            .put("id", p.id)
            .put("name", p.name)
            .put("createdAtMs", stored.createdAtMs)
            .put("modifiedAtMs", stored.modifiedAtMs)
            .put("videoClips", JSONArray().also { a -> p.timeline.videoClips.forEach { a.put(videoClip(it)) } })
            .put("audioTracks", JSONArray().also { a -> p.timeline.audioTracks.forEach { a.put(audioTrack(it)) } })
            .also { o -> if (p.timeline.pipClips.isNotEmpty()) o.put("pipClips", JSONArray().also { a -> p.timeline.pipClips.forEach { a.put(pipClip(it)) } }) }
            .put("export", export(p.export))
            .also { o -> if (p.subtitleTracks.isNotEmpty()) o.put("subtitleTracks", JSONArray().also { a -> p.subtitleTracks.forEach { a.put(SubtitleTrackJson.encode(it)) } }) }
            .also { o -> if (p.textOverlays.isNotEmpty()) o.put("textOverlays", JSONArray().also { a -> p.textOverlays.forEach { a.put(TextOverlayJson.encode(it)) } }) }
            .also { o -> if (!p.canvas.isDefault) o.put("canvas", CanvasJson.encode(p.canvas)) }
            .also { o -> if (!p.exportChoices.isDefault) o.put("exportChoices", JSONObject().put("quality", p.exportChoices.quality.name).put("size", p.exportChoices.size.name)) }
            .toString()
    }

    fun decode(json: String): DecodeResult = try {
        val o = JSONObject(json)
        val version = o.getInt("version")
        if (version != VERSION) DecodeResult.Corrupt("unsupported version $version") else {
            val videoClips = o.getJSONArray("videoClips").let { a -> (0 until a.length()).map { videoClip(a.getJSONObject(it)) } }
            val tracks = o.getJSONArray("audioTracks").let { a -> (0 until a.length()).map { audioTrack(a.getJSONObject(it)) } }
            val project = VideoProject(
                id = o.getString("id"),
                name = o.getString("name"),
                timeline = Timeline(videoClips, tracks, o.optJSONArray("pipClips")?.let { a -> (0 until a.length()).mapNotNull { runCatching { pipClip(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()).normalized(),
                export = export(o.getJSONObject("export")),
                subtitleTracks = o.optJSONArray("subtitleTracks")?.let { a -> (0 until a.length()).map { SubtitleTrackJson.decode(a.getJSONObject(it)) } } ?: emptyList(),
                textOverlays = o.optJSONArray("textOverlays")?.let { a -> (0 until a.length()).map { TextOverlayJson.decode(a.getJSONObject(it)) } } ?: emptyList(),
                canvas = o.optJSONObject("canvas")?.let(CanvasJson::decode) ?: io.bsoft.bconverter.domain.canvas.CanvasSettings(),
                // Older projects have no "exportChoices"; an unknown name falls back to the default for that choice.
                exportChoices = o.optJSONObject("exportChoices")?.let { c ->
                    io.bsoft.bconverter.domain.model.ExportChoices(
                        runCatching { io.bsoft.bconverter.domain.model.ExportPreset.valueOf(c.optString("quality")) }.getOrDefault(io.bsoft.bconverter.domain.model.ExportPreset.BALANCED),
                        runCatching { io.bsoft.bconverter.domain.model.ExportSize.valueOf(c.optString("size")) }.getOrDefault(io.bsoft.bconverter.domain.model.ExportSize.AUTO),
                    )
                } ?: io.bsoft.bconverter.domain.model.ExportChoices(),
            )
            DecodeResult.Decoded(StoredProject(project, o.getLong("createdAtMs"), o.getLong("modifiedAtMs")))
        }
    } catch (e: Exception) { // JSONException, IllegalArgumentException (violated invariant), unknown enum names, bad casts
        DecodeResult.Corrupt(e.message ?: e.javaClass.simpleName)
    }

    // ---------------- encode helpers ----------------
    private fun source(s: MediaSource): JSONObject = when (s) {
        is MediaSource.UriSource -> JSONObject().put("type", "uri").put("value", s.uri.toString())
        is MediaSource.FileSource -> JSONObject().put("type", "file").put("value", s.file.path)
    }

    private fun videoClip(c: VideoClip) = JSONObject()
        .put("id", c.id).put("source", source(c.source)).put("sourceDurationMs", c.sourceDurationMs)
        .put("sourceStartMs", c.sourceStartMs).put("sourceEndMs", c.sourceEndMs)
        .put("speed", c.playbackSpeed.toDouble())
        .also { o -> if (c.audioMuted) o.put("audioMuted", true) }
        .also { o -> if (!c.filter.isIdentity) o.put("filter", ClipFilterJson.encode(c.filter)) }
        .also { o -> if (!c.transform.isIdentity) o.put("transform", ClipTransformJson.encode(c.transform)) }
        .also { o -> if (!c.kenBurns.isNone) o.put("kenBurns", ClipTransformJson.encodeMotion(c.kenBurns)) }
        .also { o -> if (c.reversed) o.put("reversed", true) }
        .also { o -> if (c.freezeMs > 0) o.put("freezeMs", c.freezeMs) }
        .also { o -> if (c.volume != 1f) o.put("volume", c.volume.toDouble()) }
        .also { o -> if (c.fadeInMs > 0) o.put("fadeInMs", c.fadeInMs) }
        .also { o -> if (c.fadeOutMs > 0) o.put("fadeOutMs", c.fadeOutMs) }
        .also { o -> if (!c.transitionIn.isNone) o.put("transition", JSONObject().put("type", c.transitionIn.type.name).put("durationMs", c.transitionIn.durationMs)) }

    private fun audioClip(c: AudioClip) = JSONObject()
        .put("id", c.id).put("source", source(c.source)).put("sourceDurationMs", c.sourceDurationMs)
        .put("sourceStartMs", c.sourceStartMs).put("sourceEndMs", c.sourceEndMs)
        .put("volume", c.volume.toDouble()).put("startTimeMs", c.startTimeMs)
        .also { o -> if (c.playbackSpeed != 1f) o.put("speed", c.playbackSpeed.toDouble()) }
        .also { o -> if (c.fadeInMs > 0) o.put("fadeInMs", c.fadeInMs) }
        .also { o -> if (c.fadeOutMs > 0) o.put("fadeOutMs", c.fadeOutMs) }

    private fun pipClip(c: PipClip) = JSONObject()
        .put("id", c.id).put("source", source(c.source)).put("sourceDurationMs", c.sourceDurationMs)
        .put("sourceStartMs", c.sourceStartMs).put("sourceEndMs", c.sourceEndMs).put("startTimeMs", c.startTimeMs)
        .put("x", c.layout.centerX.toDouble()).put("y", c.layout.centerY.toDouble()).put("width", c.layout.width.toDouble())
        .also { o -> if (c.layout.opacity != 1f) o.put("opacity", c.layout.opacity.toDouble()) }
        .also { o -> if (c.volume != 1f) o.put("volume", c.volume.toDouble()) }
        .also { o -> if (c.layout.borderWidth > 0f) o.put("border", c.layout.borderWidth.toDouble()).put("borderColor", c.layout.borderRgb) }
        .also { o -> c.chromaKey?.let { k -> o.put("key", JSONObject().put("color", k.rgb).put("similarity", k.similarity.toDouble()).put("blend", k.blend.toDouble())) } }
        .also { o -> if (c.shape != io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE) o.put("shape", c.shape.name) }
        .also { o -> if (c.keyframes.isNotEmpty()) o.put("keys", org.json.JSONArray().also { a -> c.keyframes.forEach { k -> a.put(JSONObject().put("t", k.offsetMs).put("x", k.x.toDouble()).put("y", k.y.toDouble()).put("w", k.width.toDouble()).put("o", k.opacity.toDouble()).also { j -> if (k.eased) j.put("e", true) }) } }) }
        .also { o -> if (c.speed != 1f) o.put("speed", c.speed.toDouble()) }
        .also { o -> c.motion?.let { m -> o.put("motion", JSONObject().put("x", m.endX.toDouble()).put("y", m.endY.toDouble()).put("eased", m.eased).also { j -> m.endWidth?.let { j.put("width", it.toDouble()) } }) } }
        .also { o -> if (c.fadeInMs > 0) o.put("fadeInMs", c.fadeInMs) }
        .also { o -> if (c.fadeOutMs > 0) o.put("fadeOutMs", c.fadeOutMs) }

    private fun audioTrack(t: AudioTrack) = JSONObject()
        .put("id", t.id).put("clips", JSONArray().also { a -> t.clips.forEach { a.put(audioClip(it)) } })

    private fun export(e: ExportSettings): JSONObject {
        val video = JSONObject()
            .put("codec", e.video.codec.name)
            .put("quality", when (val q = e.video.quality) {
                is VideoQuality.Crf -> JSONObject().put("type", "crf").put("value", q.value)
                is VideoQuality.Bitrate -> JSONObject().put("type", "bitrate").put("value", q.kbps)
            })
        e.video.resolution?.let { video.put("width", it.width).put("height", it.height) }
        e.video.frameRate?.let { video.put("frameRate", it) }
        e.video.profile?.let { video.put("profile", it) }
        e.video.level?.let { video.put("level", it) }
        e.video.pixelFormat?.let { video.put("pixelFormat", it) }
        val out = JSONObject()
            .put("container", e.container.name).put("video", video).put("keepOriginalAudio", e.keepOriginalAudio)
        e.audio?.let { a ->
            val audio = JSONObject().put("codec", a.codec.name)
            a.bitrateKbps?.let { audio.put("bitrateKbps", it) }
            a.sampleRateHz?.let { audio.put("sampleRateHz", it) }
            a.channels?.let { audio.put("channels", it) }
            out.put("audio", audio)
        }
        return out
    }

    // ---------------- decode helpers ----------------
    private fun source(o: JSONObject): MediaSource = when (val type = o.getString("type")) {
        "uri" -> MediaSource.UriSource(Uri.parse(o.getString("value")))
        "file" -> MediaSource.FileSource(File(o.getString("value")))
        else -> throw IllegalArgumentException("unknown source type $type")
    }

    private fun videoClip(o: JSONObject) = VideoClip(
        id = o.getString("id"), source = source(o.getJSONObject("source")),
        sourceDurationMs = o.getLong("sourceDurationMs"), sourceStartMs = o.getLong("sourceStartMs"),
        sourceEndMs = o.getLong("sourceEndMs"), playbackSpeed = o.getDouble("speed").toFloat(),
        audioMuted = o.optBoolean("audioMuted", false),
        // Older projects have no "filter": the clip is untouched.
        filter = o.optJSONObject("filter")?.let { ClipFilterJson.decode(it) } ?: ClipFilter.None,
        reversed = o.optBoolean("reversed", false),
        // A freeze length outside the allowed range makes the clip an ordinary one rather than failing the project.
        freezeMs = o.optLong("freezeMs", 0).let { if (it in FreezeFrame.MIN_MS..FreezeFrame.MAX_MS) it else 0L },
        // Older projects have no sound settings: full volume, no fades. Out-of-range values are clamped rather than refused.
        volume = o.optDouble("volume", 1.0).takeIf { it.isFinite() }?.toFloat()?.coerceIn(ClipSound.MIN_VOLUME, ClipSound.MAX_VOLUME) ?: 1f,
        fadeInMs = o.optLong("fadeInMs", 0).coerceIn(0, ClipSound.MAX_FADE_MS),
        fadeOutMs = o.optLong("fadeOutMs", 0).coerceIn(0, ClipSound.MAX_FADE_MS),
        // Older projects have no "transform": the picture is shown whole and upright.
        transform = o.optJSONObject("transform")?.let { ClipTransformJson.decode(it) } ?: ClipTransform.None,
        // Older projects have no "kenBurns": no motion.
        kenBurns = o.optJSONObject("kenBurns")?.let { ClipTransformJson.decodeMotion(it) } ?: io.bsoft.bconverter.domain.transform.KenBurns.None,
        // Older projects have no "transition": a plain cut.
        transitionIn = o.optJSONObject("transition")?.let { Transition(TransitionType.valueOf(it.getString("type")), it.getLong("durationMs")) } ?: Transition.None,
    )

    private fun audioClip(o: JSONObject) = AudioClip(
        id = o.getString("id"), source = source(o.getJSONObject("source")),
        sourceDurationMs = o.getLong("sourceDurationMs"), sourceStartMs = o.getLong("sourceStartMs"),
        sourceEndMs = o.getLong("sourceEndMs"), volume = o.getDouble("volume").toFloat(), startTimeMs = o.getLong("startTimeMs"),
        playbackSpeed = o.optDouble("speed", 1.0).toFloat(),
        fadeInMs = o.optLong("fadeInMs", 0).coerceIn(0, ClipSound.MAX_FADE_MS),
        fadeOutMs = o.optLong("fadeOutMs", 0).coerceIn(0, ClipSound.MAX_FADE_MS),
    )

    /** Lenient: a layout out of range is clamped into range; a clip that still cannot be built is dropped by the caller. */
    private fun pipClip(o: JSONObject): PipClip {
        fun f(key: String, default: Double, lo: Float, hi: Float) = o.optDouble(key, default).takeIf { it.isFinite() }?.toFloat()?.coerceIn(lo, hi) ?: default.toFloat()
        return PipClip(
            id = o.getString("id"), source = source(o.getJSONObject("source")), sourceDurationMs = o.getLong("sourceDurationMs"),
            sourceStartMs = o.getLong("sourceStartMs"), sourceEndMs = o.getLong("sourceEndMs"), startTimeMs = o.getLong("startTimeMs"),
            layout = PipLayout(f("x", 0.75, 0f, 1f), f("y", 0.25, 0f, 1f), f("width", PipLayout.DEFAULT_WIDTH.toDouble(), PipLayout.MIN_WIDTH, PipLayout.MAX_WIDTH), f("opacity", 1.0, PipLayout.MIN_OPACITY, 1f), f("border", 0.0, 0f, PipLayout.MAX_BORDER), o.optLong("borderColor", 0xFFFFFF).coerceIn(0L, 0xFFFFFFL).toInt()),
            volume = f("volume", 1.0, 0f, 2f),
            shape = o.optString("shape", "").let { n -> io.bsoft.bconverter.domain.timeline.PipShape.values().firstOrNull { it.name == n } } ?: io.bsoft.bconverter.domain.timeline.PipShape.RECTANGLE,
            keyframes = keyframes(o),
            speed = f("speed", 1.0, VideoClip.MIN_SPEED, VideoClip.MAX_SPEED),
            chromaKey = o.optJSONObject("key")?.let { k -> runCatching { ChromaKey(k.optLong("color", 0x00FF00).coerceIn(0L, 0xFFFFFFL).toInt(), k.optDouble("similarity", 0.2).toFloat().coerceIn(ChromaKey.MIN_SIMILARITY, ChromaKey.MAX_SIMILARITY), k.optDouble("blend", 0.1).toFloat().coerceIn(0f, ChromaKey.MAX_BLEND)) }.getOrNull() },
            motion = o.optJSONObject("motion")?.let { m -> runCatching { PipMotion(m.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f), m.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f), m.optBoolean("eased", true), if (m.has("width")) m.optDouble("width", 0.0).toFloat() else null) }.getOrNull() },
            fadeInMs = o.optLong("fadeInMs", 0).coerceIn(0L, 10_000L), fadeOutMs = o.optLong("fadeOutMs", 0).coerceIn(0L, 10_000L),
        )
    }

    /** Lenient: a bad keyframe is skipped, the rest are sorted and made unique, and no more than the limit are kept. */
    private fun keyframes(o: JSONObject): List<io.bsoft.bconverter.domain.timeline.PipKeyframe> {
        val a = o.optJSONArray("keys") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val k = a.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                io.bsoft.bconverter.domain.timeline.PipKeyframe(
                    k.optLong("t", 0).coerceAtLeast(0), k.optDouble("x", 0.5).toFloat(), k.optDouble("y", 0.5).toFloat(), k.optDouble("w", 0.35).toFloat(), k.optDouble("o", 1.0).toFloat(), k.optBoolean("e", false),
                )
            }.getOrNull()
        }.sortedBy { it.offsetMs }.distinctBy { it.offsetMs }.take(io.bsoft.bconverter.domain.timeline.PipKeyframes.MAX)
    }

    private fun audioTrack(o: JSONObject) = AudioTrack(
        o.getString("id"), o.getJSONArray("clips").let { a -> (0 until a.length()).map { audioClip(a.getJSONObject(it)) } },
    )

    private fun export(o: JSONObject): ExportSettings {
        val v = o.getJSONObject("video")
        val q = v.getJSONObject("quality")
        val quality = when (val type = q.getString("type")) {
            "crf" -> VideoQuality.Crf(q.getInt("value"))
            "bitrate" -> VideoQuality.Bitrate(q.getInt("value"))
            else -> throw IllegalArgumentException("unknown quality type $type")
        }
        val video = VideoSettings(
            codec = VideoCodec.valueOf(v.getString("codec")),
            quality = quality,
            resolution = if (v.has("width")) Resolution(v.getInt("width"), v.getInt("height")) else null,
            frameRate = if (v.has("frameRate")) v.getDouble("frameRate") else null,
            profile = if (v.has("profile")) v.getString("profile") else null,
            level = if (v.has("level")) v.getString("level") else null,
            pixelFormat = if (v.has("pixelFormat")) v.getString("pixelFormat") else null,
        )
        val audio = if (o.has("audio")) o.getJSONObject("audio").let { a ->
            AudioSettings(
                codec = AudioCodec.valueOf(a.getString("codec")),
                bitrateKbps = if (a.has("bitrateKbps")) a.getInt("bitrateKbps") else null,
                sampleRateHz = if (a.has("sampleRateHz")) a.getInt("sampleRateHz") else null,
                channels = if (a.has("channels")) a.getInt("channels") else null,
            )
        } else null
        return ExportSettings(ContainerFormat.valueOf(o.getString("container")), video, audio, o.getBoolean("keepOriginalAudio"))
    }
}
