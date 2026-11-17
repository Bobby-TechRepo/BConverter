package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.SubtitleStream
import io.bsoft.bconverter.domain.model.VideoStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import org.json.JSONException
import org.json.JSONObject

sealed interface FfprobeParseResult {
    data class Parsed(val metadata: MediaMetadata) : FfprobeParseResult
    data class Rejected(val failure: ProbeFailure) : FfprobeParseResult
}

/** Pure translation of ffprobe JSON into domain models. */
class FfprobeJsonParser {
    fun parse(json: String): FfprobeParseResult {
        val root = try { JSONObject(json) } catch (_: JSONException) { return invalid() }
        val format = root.optJSONObject("format") ?: return invalid()

        val video = mutableListOf<VideoStream>()
        val audio = mutableListOf<AudioStream>()
        val subtitles = mutableListOf<SubtitleStream>()
        var longestStreamMs = 0L

        val streams = root.optJSONArray("streams")
        for (i in 0 until (streams?.length() ?: 0)) {
            val s = streams?.optJSONObject(i) ?: continue
            longestStreamMs = maxOf(longestStreamMs, s.secondsToMs("duration") ?: 0L)
            val index = s.optInt("index", i)
            when (s.optString("codec_type")) {
                "video" -> if (!s.isAttachedPicture()) video += videoStream(index, s)
                "audio" -> audio += AudioStream(
                    index, s.text("codec_name"), s.text("sample_rate")?.toIntOrNull(), s.text("channels")?.toIntOrNull(),
                    s.text("bit_rate")?.toLongOrNull(), s.language(),
                )
                "subtitle" -> subtitles += SubtitleStream(
                    index, s.language(), s.text("codec_name"),
                    title = s.optJSONObject("tags")?.text("title"),
                    isDefault = s.optJSONObject("disposition")?.optInt("default") == 1,
                    isForced = s.optJSONObject("disposition")?.optInt("forced") == 1,
                )
            }
        }
        if (video.isEmpty() && audio.isEmpty() && subtitles.isEmpty()) {
            return FfprobeParseResult.Rejected(ProbeFailure.NoMediaStreams)
        }

        val tags = format.optJSONObject("tags")
        return FfprobeParseResult.Parsed(
            MediaMetadata(
                durationMs = format.secondsToMs("duration") ?: longestStreamMs,
                container = container(format.text("format_name"), format.text("filename"), tags?.text("major_brand")),
                videoStreams = video, audioStreams = audio, subtitleStreams = subtitles,
                sizeBytes = format.text("size")?.toLongOrNull(),
                bitrateBps = format.text("bit_rate")?.toLongOrNull(),
            ),
        )
    }

    private fun invalid() = FfprobeParseResult.Rejected(ProbeFailure.InvalidOutput)

    private fun videoStream(index: Int, s: JSONObject) = VideoStream(
        index = index,
        codec = s.text("codec_name"),
        width = s.optInt("width"),
        height = s.optInt("height"),
        frameRate = frameRate(s.text("avg_frame_rate")) ?: frameRate(s.text("r_frame_rate")),
        bitrateBps = s.text("bit_rate")?.toLongOrNull(),
        rotationDegrees = rotation(s),
        pixelFormat = s.text("pix_fmt"),
    )

    private fun JSONObject.isAttachedPicture() = optJSONObject("disposition")?.optInt("attached_pic") == 1

    /** Display-matrix side data is counter-clockwise; the legacy `rotate` tag is clockwise. */
    private fun rotation(s: JSONObject): Int {
        val sideData = s.optJSONArray("side_data_list")
        for (i in 0 until (sideData?.length() ?: 0)) {
            val entry = sideData?.optJSONObject(i) ?: continue
            if (entry.has("rotation")) return normalise(-entry.optDouble("rotation"))
        }
        val tag = s.optJSONObject("tags")?.text("rotate")?.toDoubleOrNull()
        return if (tag != null) normalise(tag) else 0
    }

    private fun normalise(degrees: Double): Int = (((degrees / 90.0).roundToInt() * 90) % 360 + 360) % 360

    private fun frameRate(value: String?): Double? {
        if (value == null) return null
        val parts = value.split('/')
        val rate = if (parts.size == 2) {
            val den = parts[1].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            (parts[0].toDoubleOrNull() ?: return null) / den
        } else value.toDoubleOrNull()
        return rate?.takeIf { it > 0 }
    }

    private fun container(formatName: String?, filename: String?, brand: String?): ContainerFormat? {
        val names = formatName?.split(',')?.map { it.trim() }.orEmpty()
        val b = brand?.lowercase().orEmpty()
        return when {
            "mov" in names || "mp4" in names -> when {
                b.startsWith("qt") -> ContainerFormat.MOV
                b.startsWith("3g") -> ContainerFormat.THREE_GP
                else -> ContainerFormat.MP4
            }
            "matroska" in names ->
                if (filename?.endsWith(".webm", ignoreCase = true) == true) ContainerFormat.WEBM else ContainerFormat.MKV
            "webm" in names -> ContainerFormat.WEBM
            "avi" in names -> ContainerFormat.AVI
            "mpegts" in names -> ContainerFormat.MPEG_TS
            else -> null
        }
    }

    /** Non-blank string value, treating missing, null and ffprobe's "N/A" as absent. */
    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "N/A" }
    }

    private fun JSONObject.secondsToMs(key: String): Long? =
        text(key)?.toDoubleOrNull()?.let { (it * 1000).roundToLong() }

    private fun JSONObject.language(): String? =
        optJSONObject("tags")?.text("language")?.takeIf { it != "und" }
}
