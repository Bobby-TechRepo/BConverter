package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.media.api.ConversionRequest
import java.math.BigDecimal
import java.math.RoundingMode

/** FFmpeg encoder names for the installed build; override entries to match what the build really contains. */
data class EncoderNames(val video: Map<VideoCodec, String>, val audio: Map<AudioCodec, String>) {
    companion object {
        val Default = EncoderNames(
            video = mapOf(
                VideoCodec.H264 to "libx264", VideoCodec.HEVC to "libx265", VideoCodec.VP8 to "libvpx",
                VideoCodec.VP9 to "libvpx-vp9", VideoCodec.AV1 to "libsvtav1", VideoCodec.MPEG4 to "mpeg4",
                VideoCodec.MPEG2 to "mpeg2video",
            ),
            audio = mapOf(
                AudioCodec.AAC to "aac", AudioCodec.MP3 to "libmp3lame", AudioCodec.OPUS to "libopus",
                AudioCodec.VORBIS to "libvorbis", AudioCodec.FLAC to "flac", AudioCodec.PCM to "pcm_s16le",
                AudioCodec.AC3 to "ac3",
            ),
        )
    }
}

/** Assumes the request has already passed [io.bsoft.bconverter.domain.usecase.ConversionRequestValidator]. */
class DefaultFfmpegCommandBuilder(private val encoders: EncoderNames = EncoderNames.Default) : FfmpegCommandBuilder {

    override fun buildConversionCommand(request: ConversionRequest): FfmpegCommand {
        val settings = request.settings
        val o = mutableListOf<Pair<String, String?>>()

        o += "-f" to formatName(settings.container)
        o += "-map" to "0:v:0"
        if (settings.audio != AudioPlan.None) o += "-map" to "0:a:0"
        o += "-sn" to null
        o += "-dn" to null

        when (val video = settings.video) {
            VideoPlan.Copy -> o += "-c:v" to "copy"
            is VideoPlan.Encode -> videoOptions(o, video.settings, settings.container)
        }
        when (val audio = settings.audio) {
            AudioPlan.None -> o += "-an" to null
            AudioPlan.Copy -> o += "-c:a" to "copy"
            is AudioPlan.Encode -> {
                val s = audio.settings
                o += "-c:a" to requireNotNull(encoders.audio[s.codec]) { "No encoder name for ${s.codec}" }
                s.bitrateKbps?.let { o += "-b:a" to "${it}k" }
                s.sampleRateHz?.let { o += "-ar" to it.toString() }
                s.channels?.let { o += "-ac" to it.toString() }
            }
        }
        if (settings.container in FASTSTART_CONTAINERS) o += "-movflags" to "+faststart"

        return FfmpegCommand(
            globalOptions = listOf("-y", "-hide_banner", "-nostats", "-progress", "pipe:1"),
            inputs = listOf(FfmpegInput(request.input.file.absolutePath)),
            output = FfmpegOutput(request.outputFile.absolutePath, FfmpegOptions(o)),
        )
    }

    private fun videoOptions(o: MutableList<Pair<String, String?>>, s: VideoSettings, container: ContainerFormat) {
        o += "-c:v" to requireNotNull(encoders.video[s.codec]) { "No encoder name for ${s.codec}" }
        when (val q = s.quality) {
            is VideoQuality.Crf -> {
                o += "-crf" to q.value.toString()
                if (s.codec == VideoCodec.VP9) o += "-b:v" to "0" // libvpx-vp9 ignores CRF without this
            }
            is VideoQuality.Bitrate -> o += "-b:v" to "${q.kbps}k"
        }
        s.resolution?.let { o += "-vf" to "scale=${it.width}:${it.height}" }
        s.frameRate?.let { o += "-r" to formatFrameRate(it) }
        o += "-pix_fmt" to (s.pixelFormat ?: DEFAULT_PIXEL_FORMAT)
        s.profile?.let { o += "-profile:v" to it }
        s.level?.let { o += "-level" to it }
        if (s.codec == VideoCodec.HEVC && container in FASTSTART_CONTAINERS) o += "-tag:v" to "hvc1"
    }

    private fun formatFrameRate(fps: Double): String =
        BigDecimal.valueOf(fps).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun formatName(c: ContainerFormat) = when (c) {
        ContainerFormat.MP4 -> "mp4"
        ContainerFormat.MKV -> "matroska"
        ContainerFormat.THREE_GP -> "3gp"
        ContainerFormat.WEBM -> "webm"
        ContainerFormat.MOV -> "mov"
        ContainerFormat.AVI -> "avi"
        ContainerFormat.MPEG_TS -> "mpegts"
    }

    private companion object {
        const val DEFAULT_PIXEL_FORMAT = "yuv420p"
        val FASTSTART_CONTAINERS = setOf(ContainerFormat.MP4, ContainerFormat.MOV, ContainerFormat.THREE_GP)
    }
}
