package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.SubtitleExtractionSettings
import io.bsoft.bconverter.domain.model.SubtitleKind
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.media.api.SubtitleExtractionRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real FFmpegKit: builds an MKV with an SRT and an ASS track, extracts each, and validates the output with FFprobe. */
class SubtitleExtractionIntegrationTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "it-subs").apply { mkdirs() }
    private val probe = FfmpegKitFfprobeRunner(Dispatchers.IO)
    private val parser = FfprobeJsonParser()
    private val mkv = File(dir, "subs.mkv")

    private fun metadataOf(file: File): MediaMetadata = runBlocking {
        val run = probe.run(file) as FfprobeRunResult.Success
        (parser.parse(run.json) as FfprobeParseResult.Parsed).metadata
    }

    @Before fun fixture() {
        val srt = File(dir, "a.srt").apply { writeText("1\n00:00:00,200 --> 00:00:01,200\nHello\n\n2\n00:00:01,300 --> 00:00:02,000\nWorld\n") }
        val ass = File(dir, "b.ass").apply {
            writeText(
                "[Script Info]\nScriptType: v4.00+\nPlayResX: 384\nPlayResY: 288\n\n[V4+ Styles]\n" +
                    "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
                    "Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1\n\n[Events]\n" +
                    "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                    "Dialogue: 0,0:00:00.50,0:00:01.50,Default,,0,0,0,,Bonjour\n",
            )
        }
        if (mkv.exists() && mkv.length() > 0) return // generated once, shared by every test
        val cmd = "-y -f lavfi -i testsrc=d=2:s=160x120:r=10 -i ${srt.absolutePath} -i ${ass.absolutePath} " +
            "-map 0:v -map 1 -map 2 -c:v mpeg4 -c:s:0 srt -c:s:1 ass " +
            "-metadata:s:s:0 language=eng -metadata:s:s:0 title=English -metadata:s:s:1 language=fra ${mkv.absolutePath}"
        assertTrue(ReturnCode.isSuccess(FFmpegKit.execute(cmd).returnCode))
    }

    private fun extract(ordinal: Int, output: SubtitleOutput, name: String): File {
        val meta = metadataOf(mkv)
        val out = File(dir, name).apply { delete() }
        val request = SubtitleExtractionRequest("sub-$name-${System.nanoTime()}", ResolvedInput(mkv, "subs.mkv", mkv.length(), false), meta, out, SubtitleExtractionSettings(ordinal, output))
        val result = runBlocking { kotlinx.coroutines.withTimeout(30_000) { FfmpegKitExecutor().execute(request.jobId, DefaultFfmpegCommandBuilder().buildSubtitleExtractionCommand(request).toArguments()) { _, _ -> } } }
        assertEquals(FfmpegExecResult.Success, result)
        return out
    }

    @Test fun probe_reports_both_tracks_with_language_title_and_kind() {
        val subs = metadataOf(mkv).subtitleStreams
        assertEquals(listOf("eng", "fra"), subs.map { it.language }); assertEquals("English", subs[0].title)
        assertTrue(subs.all { it.kind == SubtitleKind.TEXT })
    }

    @Test fun an_srt_track_is_copied_as_a_valid_srt_file() {
        val out = extract(0, SubtitleOutput.SRT, "out.srt")
        val text = out.readText()
        assertTrue(text, text.contains("00:00:00,200 --> 00:00:01,200") && text.contains("Hello") && text.contains("World"))
        assertEquals("subrip", metadataOf(out).subtitleStreams.single().codec)
    }

    @Test fun an_ass_track_keeps_its_styles_when_extracted_as_ass() {
        val text = extract(1, SubtitleOutput.ASS, "out.ass").readText()
        assertTrue(text, text.contains("[V4+ Styles]") && text.contains("Bonjour"))
    }

    @Test fun an_ass_track_can_be_converted_to_srt_and_webvtt() {
        assertTrue(extract(1, SubtitleOutput.SRT, "conv.srt").readText().contains("Bonjour"))
        val vtt = extract(1, SubtitleOutput.WEBVTT, "conv.vtt").readText()
        assertTrue(vtt, vtt.startsWith("WEBVTT") && vtt.contains("Bonjour"))
    }

    @Test fun an_srt_track_converts_to_ass() {
        val out = extract(0, SubtitleOutput.ASS, "conv.ass")
        assertTrue(out.readText().contains("Hello")); assertEquals("ass", metadataOf(out).subtitleStreams.single().codec)
    }
}
