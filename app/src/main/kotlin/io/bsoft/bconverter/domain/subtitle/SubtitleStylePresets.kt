package io.bsoft.bconverter.domain.subtitle

data class SubtitleStylePreset(val id: String, val style: SubtitleStyle)

/** Ready-made looks for the subtitle appearance editor. Colours are ARGB. */
object SubtitleStylePresets {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val YELLOW = 0xFFFFE23A.toInt()

    val CLASSIC = SubtitleStylePreset("classic", SubtitleStyle(fontSize = 48f, primaryColor = WHITE, outlineColor = BLACK, outlineWidth = 2f, shadow = 1f))
    val BOLD_YELLOW = SubtitleStylePreset("bold_yellow", SubtitleStyle(fontSize = 52f, bold = true, primaryColor = YELLOW, outlineColor = BLACK, outlineWidth = 3f, shadow = 1f))
    val BOXED = SubtitleStylePreset("boxed", SubtitleStyle(fontSize = 46f, primaryColor = WHITE, outlineColor = 0xB3000000.toInt(), backColor = 0xB3000000.toInt(), boxed = true, outlineWidth = 6f, shadow = 0f))
    val CINEMATIC = SubtitleStylePreset("cinematic", SubtitleStyle(fontSize = 44f, italic = true, primaryColor = 0xFFF2F2F2.toInt(), outlineColor = BLACK, outlineWidth = 1.5f, shadow = 2f, marginV = 60))
    val TOP = SubtitleStylePreset("top", SubtitleStyle(fontSize = 48f, primaryColor = WHITE, outlineColor = BLACK, outlineWidth = 2f, alignment = SubtitleAlignment.TOP_CENTER, marginV = 40))
    val LARGE = SubtitleStylePreset("large", SubtitleStyle(fontSize = 72f, bold = true, primaryColor = WHITE, outlineColor = BLACK, outlineWidth = 4f, shadow = 2f))

    val all: List<SubtitleStylePreset> = listOf(CLASSIC, BOLD_YELLOW, BOXED, CINEMATIC, TOP, LARGE)

    /** The colours offered by the appearance editor. */
    val colors: List<Int> = listOf(WHITE, YELLOW, 0xFF7CFC00.toInt(), 0xFF00E5FF.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFA500.toInt(), 0xFFFF80FF.toInt(), BLACK)
}
