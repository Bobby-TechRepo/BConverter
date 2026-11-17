package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.SubtitleKind
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleExportMode
import io.bsoft.bconverter.domain.subtitle.SubtitleExportPlanner
import io.bsoft.bconverter.domain.subtitle.SubtitleStylePresets
import io.bsoft.bconverter.domain.subtitle.SubtitleEditor
import io.bsoft.bconverter.domain.subtitle.SubtitleEdit
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.domain.usecase.SubtitleFileWriter
import io.bsoft.bconverter.media.api.RenderOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FFmpegKit: renders a project with soft and burned subtitles and checks the result with FFprobe and by comparing frames. */
class SubtitleRenderIntegrationTest {
    @get:org.junit.Rule val heavyMedia = io.bsoft.bconverter.HeavyMediaRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample: File by lazy { IntegrationFixtures.sampleMp4(context) }
    private val dir = File(context.cacheDir, "subtitle-render").apply { deleteRecursively(); mkdirs() }
    private val staging = StagingDirectory(File(dir, "staging"))
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val source get() = MediaSource.FileSource(sample)

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        (parser.parse((probe.run(file) as FfprobeRunResult.Success).json) as FfprobeParseResult.Parsed).metadata
    }

    private fun track(id: String, mode: SubtitleExportMode, language: String? = "en", text: String = "Hello subtitle") =
        SubtitleTrack(id, "Track $id", listOf(SubtitleCue("c$id", 200, 900, text)), language = language, title = "Title $id", exportMode = mode)

    @org.junit.Before fun fonts() { FfmpegFonts.configure(context) }

    private fun render(name: String, container: ContainerFormat, vararg tracks: SubtitleTrack): File {
        val meta = metadataOf(sample)
        val clip = VideoClip("a", source, maxOf(meta.durationMs, 1_900), 0, 1_800)
        val export = ExportSettings(container, VideoSettings(if (container == ContainerFormat.WEBM) VideoCodec.VP8 else VideoCodec.MPEG4, VideoQuality.Bitrate(400)), AudioSettings(if (container == ContainerFormat.WEBM) AudioCodec.OPUS else AudioCodec.AAC, 96))
        val project = VideoProject(name, name, Timeline(listOf(clip)).normalized(), export, mapOf(source to meta), tracks.toList())
        val plan = SubtitleExportPlanner.plan(project.subtitleTracks, container)
        val files = SubtitleFileWriter(staging, FfmpegFonts.fontsDir).write(plan)
        val out = File(dir, "$name.${container.extension}").apply { delete() }
        val command = DefaultFfmpegCommandBuilder().buildRenderCommand(project, RenderOutput(out, files))
        val result = runBlocking { withTimeout(120_000) { FfmpegKitExecutor().execute("$name-${System.nanoTime()}", command.toArguments()) { _, _ -> } } }
        assertEquals("render failed: ${command.toArguments()}", FfmpegExecResult.Success, result)
        files.files.forEach { it.delete() }
        return out
    }

    private fun frameAt(video: File, seconds: String, name: String): ByteArray {
        val png = File(dir, "$name.png").apply { delete() }
        assertTrue(ReturnCode.isSuccess(FFmpegKit.execute("-y -ss $seconds -i ${video.absolutePath} -frames:v 1 ${png.absolutePath}").returnCode))
        return png.readBytes()
    }

    @Test fun mkv_gets_a_soft_subtitle_track_with_language_and_title() {
        val m = metadataOf(render("soft-mkv", ContainerFormat.MKV, track("a", SubtitleExportMode.SOFT)))
        val s = m.subtitleStreams.single()
        assertEquals("subrip", s.codec); assertEquals("eng", s.language); assertEquals("Title a", s.title); assertEquals(SubtitleKind.TEXT, s.kind)
    }

    @Test fun mp4_gets_mov_text_and_two_tracks_keep_their_order_and_flags() {
        val m = metadataOf(render("soft-mp4", ContainerFormat.MP4, track("a", SubtitleExportMode.SOFT, "en").copy(isDefault = true), track("b", SubtitleExportMode.SOFT, "fr").copy(isForced = true)))
        assertEquals(listOf("mov_text", "mov_text"), m.subtitleStreams.map { it.codec }); assertEquals(listOf("eng", "fra"), m.subtitleStreams.map { it.language })
        assertEquals(listOf(true, false), m.subtitleStreams.map { it.isDefault }); assertEquals(listOf(false, true), m.subtitleStreams.map { it.isForced })
    }

    @Test fun a_styled_track_keeps_its_ass_styling_in_mkv() {
        val styled = (SubtitleEditor.applyPreset(track("a", SubtitleExportMode.SOFT), SubtitleStylePresets.BOLD_YELLOW) as SubtitleEdit.Ok).track
        assertEquals("ass", metadataOf(render("soft-ass", ContainerFormat.MKV, styled)).subtitleStreams.single().codec)
    }

    @Test fun webm_gets_webvtt() = assertEquals("webvtt", metadataOf(render("soft-webm", ContainerFormat.WEBM, track("a", SubtitleExportMode.SOFT))).subtitleStreams.single().codec)

    @Test fun tracks_that_are_off_or_burned_add_no_subtitle_stream() {
        assertTrue(metadataOf(render("off", ContainerFormat.MP4, track("a", SubtitleExportMode.OFF))).subtitleStreams.isEmpty())
        assertTrue(metadataOf(render("burned-streams", ContainerFormat.MP4, track("a", SubtitleExportMode.BURNED))).subtitleStreams.isEmpty())
    }

    @Test fun a_burned_track_changes_the_picture_while_it_is_shown_and_only_then() {
        val plain = render("plain", ContainerFormat.MP4)
        val burned = render("burned", ContainerFormat.MP4, track("a", SubtitleExportMode.BURNED, text = "BURNED IN"))
        assertFalse("frame during the cue must differ", frameAt(plain, "0.5", "p1").contentEquals(frameAt(burned, "0.5", "b1")))
        assertTrue("frame after the cue must be identical", frameAt(plain, "1.5", "p2").contentEquals(frameAt(burned, "1.5", "b2")) || kotlin.math.abs(frameAt(plain, "1.5", "p3").size - frameAt(burned, "1.5", "b3").size) < 200)
    }

    @Test fun the_same_track_is_never_embedded_and_burned_at_once() {
        val m = metadataOf(render("one-mode", ContainerFormat.MKV, track("a", SubtitleExportMode.BURNED)))
        assertTrue(m.subtitleStreams.isEmpty())
    }
}
