package io.bsoft.bconverter.fakes

/** Builds `ffmpeg -encoders` / `-decoders` style output for tests. */
object FfmpegListings {
    private const val LEGEND = """
Encoders:
 V..... = Video
 A..... = Audio
 S..... = Subtitle
 ...X.. = Codec is experimental
 ------
"""
    fun of(vararg lines: String): String = LEGEND + lines.joinToString("\n") { " $it" } + "\n"
    fun video(name: String) = "V....D $name $name description"
    fun audio(name: String) = "A....D $name $name description"
    fun experimentalAudio(name: String) = "A..X.D $name $name description"
}
