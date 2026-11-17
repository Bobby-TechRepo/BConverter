package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.data.font.SystemFontScanner
import io.bsoft.bconverter.domain.font.FontFileRef
import io.bsoft.bconverter.domain.font.FontResolver
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks on a real device that the files the font code picks are ones FFmpeg can actually draw text with. */
class FontResolutionIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "font-resolution").apply { deleteRecursively(); mkdirs() }

    /**
     * Draws [text] with libass (the `subtitles` filter) using [font]'s folder as the font directory and returns the size of the PNG.
     * This FFmpegKit build has no `drawtext` filter, so text is drawn through libass; the overlays use pictures instead.
     */
    private fun drawnFrameSize(font: FontFileRef?, text: String, name: String): Long {
        val out = File(dir, "$name.png").apply { delete() }
        val fontDir = File(dir, "fonts-$name").apply { deleteRecursively(); mkdirs() }
        font?.let { File(it.path).copyTo(File(fontDir, File(it.path).name), overwrite = true) }
        val ass = File(dir, "$name.ass").apply {
            writeText(
                "[Script Info]\nScriptType: v4.00+\nPlayResX: 480\nPlayResY: 160\n\n[V4+ Styles]\n" +
                    "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
                    "Style: Default,${font?.family ?: "Roboto"},60,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,5,10,10,10,1\n\n" +
                    "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                    "Dialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,$text\n",
            )
        }
        val vf = if (text.isEmpty()) "null" else "subtitles=filename=${ass.absolutePath}:fontsdir=${fontDir.absolutePath}"
        val session = FFmpegKit.execute("-y -f lavfi -i color=c=black:s=480x160:d=1 -vf $vf -frames:v 1 ${out.absolutePath}")
        assertTrue("ffmpeg failed: ${session.allLogsAsString}", ReturnCode.isSuccess(session.returnCode))
        return out.length()
    }

    @Test fun the_device_has_system_fonts_and_the_default_family_resolves_to_a_real_file() {
        val system = SystemFontScanner().scan()
        assertTrue("no system fonts found", system.isNotEmpty())
        val r = FontResolver().resolve(system, null, 400, false)
        assertTrue(r.file != null && File(r.file!!.path).isFile)
    }

    @Test fun an_unknown_family_still_resolves_to_a_drawable_fallback() {
        val r = FontResolver().resolve(SystemFontScanner().scan(), "No Such Family 123", 700, false)
        assertFalse(r.exact); assertTrue(r.file != null && File(r.file!!.path).isFile)
    }

    @Test fun libass_draws_text_with_the_resolved_font_file() {
        val file = FontResolver().resolve(SystemFontScanner().scan(), "Roboto", 400, false).file
        assertTrue(file != null)
        val blank = drawnFrameSize(null, "", "blank")
        val text = drawnFrameSize(file, "Hello", "hello")
        assertTrue("text must change the picture ($blank vs $text)", text > blank)
    }
}
