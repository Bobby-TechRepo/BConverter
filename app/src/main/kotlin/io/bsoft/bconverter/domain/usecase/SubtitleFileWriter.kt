package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.render.BurnedSubtitleFile
import io.bsoft.bconverter.domain.render.RenderSubtitles
import io.bsoft.bconverter.domain.render.SoftSubtitleFile
import io.bsoft.bconverter.domain.subtitle.LanguageCodes
import io.bsoft.bconverter.domain.subtitle.SubtitleCodecs
import io.bsoft.bconverter.domain.subtitle.SubtitleExportPlan
import io.bsoft.bconverter.domain.subtitle.SubtitleFormat
import io.bsoft.bconverter.domain.subtitle.SubtitleStyle
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import java.io.File
import java.io.IOException

/** Writes the tracks of an export plan to temporary files in the staging directory. The track offset is applied by the codecs. */
class SubtitleFileWriter(private val staging: StagingDirectory, private val fontsDir: String? = SYSTEM_FONTS) {
    /** On failure, files already written are deleted before the [IOException] is thrown. */
    fun write(plan: SubtitleExportPlan): RenderSubtitles {
        val written = mutableListOf<File>()
        try {
            val soft = plan.soft.map { s ->
                val file = staging.newFile("subtitle.${s.codec.fileFormat.extension}")
                file.writeText(SubtitleCodecs.serialize(s.track, s.codec.fileFormat)); written += file
                SoftSubtitleFile(file, s.codec, language = s.track.language, title = s.track.title ?: s.track.name, isDefault = s.track.isDefault, isForced = s.track.isForced)
            }
            val burned = plan.burned.map { track ->
                val file = staging.newFile("burn.ass")
                file.writeText(SubtitleCodecs.serialize(withFonts(track), SubtitleFormat.ASS)); written += file
                BurnedSubtitleFile(file)
            }
            return RenderSubtitles(soft, burned, fontsDir.takeIf { burned.isNotEmpty() })
        } catch (e: IOException) {
            written.forEach { it.delete() }
            throw e
        }
    }

    /** libass finds nothing to draw with if the font is missing, so styles without a font get one every Android device has. */
    private fun withFonts(track: SubtitleTrack): SubtitleTrack {
        val styles = LinkedHashMap<String, SubtitleStyle>()
        track.styles.forEach { (name, s) -> styles[name] = if (s.fontFamily == null) s.copy(fontFamily = FALLBACK_FONT) else s }
        if (styles.isEmpty()) styles[SubtitleStyle.DEFAULT_NAME] = SubtitleStyle(fontFamily = FALLBACK_FONT)
        track.cues.mapNotNull { it.styleName }.filter { it !in styles }.forEach { styles[it] = SubtitleStyle(it, fontFamily = FALLBACK_FONT) }
        return track.copy(styles = styles)
    }

    companion object { const val SYSTEM_FONTS = "/system/fonts"; const val FALLBACK_FONT = "Roboto" }
}
