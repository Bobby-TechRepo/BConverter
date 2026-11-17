package io.bsoft.bconverter.media.ffmpeg

/** Chooses the one system font file that is copied into the app's own small font folder. */
object BaseFontPicker {
    private val candidates = listOf(
        Regex("^Roboto-Regular\\.ttf$"),
        Regex("^Roboto\\[.*\\]\\.ttf$"),
        Regex("^Roboto-Medium\\.ttf$"),
        Regex("^DroidSans\\.ttf$"),
        Regex("^NotoSans-Regular\\.[to]tf$"),
    )

    fun pick(fileNames: List<String>): String? = candidates.firstNotNullOfOrNull { rx -> fileNames.firstOrNull { rx.matches(it) } }
}
