package io.bsoft.bconverter.media.ffmpeg

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.io.File

/**
 * Tells FFmpegKit where fonts are. Without this its fontconfig has no fonts, so burned subtitles (libass) and text
 * overlays (drawtext) draw nothing. Scanning all of /system/fonts took minutes on a phone with many CJK fonts, so the app
 * keeps its own small font folder (one base font copied from the system now; downloaded fonts are added later) and points
 * FFmpeg only at that. Common names Android does not have are mapped to the base font's family, Roboto.
 */
object FfmpegFonts {
    @Volatile private var directory: File? = null

    /** The folder FFmpeg reads fonts from; null until [configure] has run. */
    val fontsDir: String? get() = directory?.path

    fun configure(context: Context): File {
        directory?.let { return it }
        synchronized(this) {
            directory?.let { return it }
            val dir = File(context.applicationContext.filesDir, "fonts").apply { mkdirs() }
            copyBaseFont(dir)
            val usable = hasBaseFont(dir)
            val path = if (usable) dir else File(SYSTEM_FONTS)
            runCatching {
                FFmpegKitConfig.setFontDirectory(
                    context.applicationContext, path.path,
                    mapOf("Arial" to "Roboto", "Helvetica" to "Roboto", "sans-serif" to "Roboto", "Default" to "Roboto"),
                )
            }
            directory = path
            return path
        }
    }

    private fun copyBaseFont(dir: File) {
        if (hasBaseFont(dir)) return
        val system = File(SYSTEM_FONTS)
        val name = BaseFontPicker.pick(system.list().orEmpty().toList()) ?: return
        runCatching {
            val target = File(dir, name)
            File(system, name).copyTo(File(dir, "$name.part"), overwrite = true).renameTo(target)
        }
    }

    private fun hasBaseFont(dir: File) = dir.listFiles().orEmpty().any { it.isFile && !it.name.startsWith(DOWNLOADED_PREFIX) && !it.name.endsWith(".part") && it.length() > 0 }

    /** Call after fonts were downloaded or removed so FFmpeg's font lookup sees the change. */
    fun refresh(context: Context) {
        val dir = directory ?: return
        runCatching { FFmpegKitConfig.setFontDirectory(context.applicationContext, dir.path, mapOf("Arial" to "Roboto", "Helvetica" to "Roboto", "sans-serif" to "Roboto", "Default" to "Roboto")) }
    }

    const val DOWNLOADED_PREFIX = "gf-"
    const val SYSTEM_FONTS = "/system/fonts"
}
