package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.render.FilterGraphBuilder
import io.bsoft.bconverter.domain.render.RenderNumbers
import io.bsoft.bconverter.domain.render.RenderPlanResult
import io.bsoft.bconverter.domain.render.RenderPlanner
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.domain.render.OverlayFilters
import io.bsoft.bconverter.media.api.RenderOutput
import io.bsoft.bconverter.domain.render.SubtitleFilter
import io.bsoft.bconverter.domain.subtitle.LanguageCodes
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
            // Stats stay enabled (no -nostats): FFmpegKit's statistics callback is fed from ffmpeg's progress reporting.
            globalOptions = listOf("-y", "-hide_banner"),
            inputs = listOf(FfmpegInput(request.input.file.absolutePath)),
            output = FfmpegOutput(request.outputFile.absolutePath, FfmpegOptions(o)),
        )
    }

    override fun buildAudioExtractionCommand(request: AudioExtractionRequest): FfmpegCommand {
        val s = request.settings
        val o = mutableListOf<Pair<String, String?>>()
        o += "-f" to audioFormatName(s.format)
        o += "-map" to "0:a:${s.streamOrdinal}"
        o += "-vn" to null
        o += "-sn" to null
        o += "-dn" to null
        o += "-map_metadata" to "0" // keep title/artist/etc.
        when (val mode = s.mode) {
            AudioExtractionMode.Copy -> o += "-c:a" to "copy"
            is AudioExtractionMode.Encode -> {
                o += "-c:a" to requireNotNull(encoders.audio[s.format.codec]) { "No encoder name for ${s.format.codec}" }
                mode.bitrateKbps?.let { o += "-b:a" to "${it}k" }
                mode.sampleRateHz?.let { o += "-ar" to it.toString() }
                mode.channels?.let { o += "-ac" to it.toString() }
            }
        }
        return FfmpegCommand(
            globalOptions = listOf("-y", "-hide_banner"),
            inputs = listOf(FfmpegInput(request.input.file.absolutePath)),
            output = FfmpegOutput(request.outputFile.absolutePath, FfmpegOptions(o)),
        )
    }

    override fun buildSubtitleExtractionCommand(request: io.bsoft.bconverter.media.api.SubtitleExtractionRequest): FfmpegCommand {
        val s = request.settings
        val stream = request.metadata.subtitleStreams[s.streamOrdinal]
        // ASS is always run through the ASS encoder (it keeps the header and styles): a plain packet copy into the
        // `ass` muxer was observed to never finish on device.
        val copy = s.output != io.bsoft.bconverter.domain.model.SubtitleOutput.ASS &&
            io.bsoft.bconverter.domain.usecase.SubtitleExtractionPlanner().isCopy(stream, s.output)
        val o = mutableListOf<Pair<String, String?>>()
        o += "-map" to "0:s:${s.streamOrdinal}"
        o += "-vn" to null
        o += "-an" to null
        o += "-dn" to null
        o += "-map_metadata" to "0" // stream language/title follow the mapped stream automatically
        o += "-c:s" to if (copy) "copy" else requireNotNull(s.output.encoder) { "${s.output} can only be copied" }
        o += "-f" to s.output.muxer
        return FfmpegCommand(
            globalOptions = listOf("-y", "-hide_banner"),
            inputs = listOf(FfmpegInput(request.input.file.absolutePath)),
            output = FfmpegOutput(request.outputFile.absolutePath, FfmpegOptions(o)),
        )
    }

    private fun audioFormatName(f: AudioFormat) = when (f) {
        AudioFormat.MP3 -> "mp3"
        AudioFormat.AAC -> "adts"
        AudioFormat.M4A -> "ipod"
        AudioFormat.FLAC -> "flac"
        AudioFormat.WAV -> "wav"
        AudioFormat.OPUS -> "opus"
    }

    override fun buildRenderCommand(project: VideoProject, output: RenderOutput): FfmpegCommand {
        val plan = when (val result = RenderPlanner.plan(project, output.keepSoundTrack)) {
            is RenderPlanResult.Success -> result.plan
            is RenderPlanResult.Failure -> throw IllegalArgumentException("The project cannot be rendered: ${result.error}")
        }
        val graph = FilterGraphBuilder.build(plan)
        val export = project.export
        val o = mutableListOf<Pair<String, String?>>()

        o += "-f" to formatName(export.container)
        // Burned-in subtitles are drawn after everything else, so they are never scaled, faded or cross-faded with the clips.
        var videoLabel = graph.videoOutput
        // Text overlays are extra looped-picture inputs, numbered after the clips and the soft subtitle files.
        val overlayChain = OverlayFilters.build(output.overlays, plan.canvas.width, plan.canvas.height, plan.inputs.size + output.subtitles.soft.size, videoLabel)
        videoLabel = overlayChain.output
        val burnChains = output.subtitles.burned.mapIndexed { i, burned ->
            val next = "vsub${i + 1}"
            "[$videoLabel]${SubtitleFilter.render(burned.file, output.subtitles.fontsDir)}[$next]".also { videoLabel = next }
        }
        o += "-filter_complex" to (listOf(graph.render()) + overlayChain.parts + burnChains).joinToString(";")
        o += "-map" to "[$videoLabel]"
        graph.audioOutput?.let { o += "-map" to "[$it]" }
        // Canvas size and frame rate are applied inside the filter graph, not as output options.
        videoOptions(o, export.video, export.container, applyScaleAndRate = false)
        val audio = export.audio
        if (graph.audioOutput != null && audio != null) {
            o += "-c:a" to requireNotNull(encoders.audio[audio.codec]) { "No encoder name for ${audio.codec}" }
            audio.bitrateKbps?.let { o += "-b:a" to "${it}k" }
            audio.sampleRateHz?.let { o += "-ar" to it.toString() }
            audio.channels?.let { o += "-ac" to it.toString() }
        } else {
            o += "-an" to null
        }
        // Soft subtitles: one extra input per track, mapped in order, each with its codec, language, title and flags.
        output.subtitles.soft.forEachIndexed { i, s ->
            o += "-map" to "${plan.inputs.size + i}:0"
            o += "-c:s:$i" to s.codec.ffmpegName
            LanguageCodes.iso639_2(s.language)?.let { o += "-metadata:s:s:$i" to "language=$it" }
            s.title?.takeIf { it.isNotBlank() }?.let { o += "-metadata:s:s:$i" to "title=$it" }
            o += "-disposition:s:$i" to listOfNotNull("default".takeIf { s.isDefault }, "forced".takeIf { s.isForced }).joinToString("+").ifEmpty { "0" }
        }
        if (export.container in FASTSTART_CONTAINERS) o += "-movflags" to "+faststart"
        o += "-t" to RenderNumbers.seconds(plan.durationMs) // audio running past the video is cut

        return FfmpegCommand(
            globalOptions = listOf("-y", "-hide_banner"),
            inputs = plan.inputs.map { input ->
                val options = mutableListOf<Pair<String, String?>>()
                if (input.seekMs > 0) options += "-ss" to RenderNumbers.seconds(input.seekMs)
                options += "-t" to RenderNumbers.seconds(input.durationMs)
                FfmpegInput(input.file.absolutePath, FfmpegOptions(options))
            } + output.subtitles.soft.map { FfmpegInput(it.file.absolutePath) } + output.overlays.map {
                FfmpegInput(it.file.absolutePath, FfmpegOptions(listOf(
                    "-loop" to "1", "-framerate" to RenderNumbers.format(plan.canvas.frameRate), "-t" to RenderNumbers.seconds(plan.durationMs),
                )))
            },
            output = FfmpegOutput(output.file.absolutePath, FfmpegOptions(o)),
        )
    }

    private fun videoOptions(
        o: MutableList<Pair<String, String?>>, s: VideoSettings, container: ContainerFormat, applyScaleAndRate: Boolean = true,
    ) {
        o += "-c:v" to requireNotNull(encoders.video[s.codec]) { "No encoder name for ${s.codec}" }
        when (val q = s.quality) {
            is VideoQuality.Crf -> {
                o += "-crf" to q.value.toString()
                if (s.codec == VideoCodec.VP9) o += "-b:v" to "0" // libvpx-vp9 ignores CRF without this
            }
            is VideoQuality.Bitrate -> o += "-b:v" to "${q.kbps}k"
        }
        if (applyScaleAndRate) {
            s.resolution?.let { o += "-vf" to "scale=${it.width}:${it.height}" }
            s.frameRate?.let { o += "-r" to formatFrameRate(it) }
        }
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
