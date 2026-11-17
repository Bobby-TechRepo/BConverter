package io.bsoft.bconverter.feature.editor

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.domain.font.FontLibrary
import io.bsoft.bconverter.domain.font.FontVariant
import io.bsoft.bconverter.domain.font.InstalledFamily
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.TextOverlayStyle
import java.io.File
import javax.inject.Inject

/** Callbacks for the text overlay UI; the route wires them to the ViewModel. */
data class TextOverlayActions(
    val onAdd: () -> Unit = {},
    val onAddImage: () -> Unit = {},
    val onSelect: (String?) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onText: (String, String) -> Unit = { _, _ -> },
    val onRetime: (String, Long, Long) -> Unit = { _, _, _ -> },
    val onShift: (String, Long) -> Unit = { _, _ -> },
    val onPosition: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onRotation: (String, Float) -> Unit = { _, _ -> },
    val onScale: (String, Float) -> Unit = { _, _ -> },
    val onWidth: (String, Float?) -> Unit = { _, _ -> },
    val onStyle: (String, (TextOverlayStyle) -> TextOverlayStyle) -> Unit = { _, _ -> },
    val onAnimations: (String, OverlayAnimation?, OverlayAnimation?) -> Unit = { _, _, _ -> },
    val onKeyframes: (String, List<io.bsoft.bconverter.domain.overlay.OverlayKeyframe>) -> Unit = { _, _ -> },
    val onLayer: (String, EditorViewModel.OverlayLayerMove) -> Unit = { _, _ -> },
    val onTransform: (dx: Float, dy: Float, zoom: Float, rotationDeg: Float) -> Unit = { _, _, _, _ -> },
    /** A drag or pinch on an overlay that has keyframes: it edits (or adds) the keyframe at the playhead, [offsetMs] after the overlay starts. */
    val onKeyframeTransform: (id: String, offsetMs: Long, dx: Float, dy: Float, zoom: Float, rotationDeg: Float) -> Unit = { _, _, _, _, _, _ -> },
    val onGestureEnd: () -> Unit = {},
)

/** What the text overlay UI needs to know about fonts. */
interface OverlayFonts {
    fun families(): List<InstalledFamily>
    fun recents(): List<String>
    /** The typeface that draws [style], or null for the default font. */
    fun typeface(style: TextOverlayStyle): FontFamily?
    /** True when the style's family is installed (so it is drawn as chosen, not with a stand-in). */
    fun isExact(style: TextOverlayStyle): Boolean
    fun markUsed(family: String)
    /** The family drawn in its own regular face, for showing its name in the picker; null for the default font. */
    fun previewFace(family: String): FontFamily? = null
}

object NoOverlayFonts : OverlayFonts {
    override fun families() = emptyList<InstalledFamily>()
    override fun recents() = emptyList<String>()
    override fun typeface(style: TextOverlayStyle): FontFamily? = null
    override fun isExact(style: TextOverlayStyle) = true
    override fun markUsed(family: String) = Unit
}

/** Backs [OverlayFonts] with the device's font library. Typefaces are created once per font file. */
@HiltViewModel
class OverlayFontsViewModel @Inject constructor(private val library: FontLibrary) : ViewModel(), OverlayFonts {
    private val faces = HashMap<String, FontFamily?>()

    override fun families(): List<InstalledFamily> = library.installedFamilies()
    override fun recents(): List<String> = library.recentFamilies()
    override fun markUsed(family: String) = library.markUsed(family)

    override fun isExact(style: TextOverlayStyle): Boolean = style.fontFamily == null || library.resolve(style.fontFamily, style.fontWeight, style.italic).exact

    override fun previewFace(family: String): FontFamily? = typeface(TextOverlayStyle(fontFamily = family))

    override fun typeface(style: TextOverlayStyle): FontFamily? {
        val family = style.fontFamily ?: return null
        val file = library.resolve(family, style.fontWeight, style.italic).file ?: return null
        // The stamp makes a re-downloaded file (same name, new bytes) get a fresh typeface instead of the cached old one.
        val stamp = File(file.path).let { "${it.length()}:${it.lastModified()}" }
        return faces.getOrPut("${file.path}|$stamp") {
            runCatching { FontFamily(Font(File(file.path), FontWeight(file.variant.weight), if (file.variant.italic) FontStyle.Italic else FontStyle.Normal)) }.getOrNull()
        }
    }

    /** Weights a family offers; every weight when it is a variable font or unknown. */
    fun weightsOf(family: String?): List<Int> {
        val variants: List<FontVariant> = family?.let { library.variantsOf(it) }.orEmpty()
        return variants.map { it.weight }.distinct().sorted().ifEmpty { listOf(100, 200, 300, 400, 500, 600, 700, 800, 900) }
    }
}
