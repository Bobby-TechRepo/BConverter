package io.bsoft.bconverter.domain.overlay

import io.bsoft.bconverter.domain.font.FontLibrary
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachedOverlayLayerPreparerImageTest {
    @get:Rule val tmp = TemporaryFolder()
    private val requests = mutableListOf<OverlayRasterRequest>()
    private val rasterizer = object : OverlayRasterizer {
        override fun render(request: OverlayRasterRequest, out: File): RasterizedOverlay? { requests += request; out.writeText("png"); return RasterizedOverlay(10, 5) }
    }
    private val settings = object : io.bsoft.bconverter.domain.settings.SettingsStore {
        override fun getString(key: String): String? = null; override fun getBoolean(key: String, default: Boolean) = default
        override fun putString(key: String, value: String) {}; override fun putBoolean(key: String, value: Boolean) {}
    }
    /** Any font lookup fails the test: pictures never need a font. */
    private fun fonts() = FontLibrary({ throw AssertionError("no font lookup for pictures") }, { throw AssertionError("no font lookup for pictures") }, io.bsoft.bconverter.domain.font.RecentFonts(settings))

    private fun prepared(vararg o: TextOverlay) = runBlocking {
        CachedOverlayLayerPreparer(OverlayLayerCache(tmp.newFolder("c"), rasterizer), fonts()).prepare(o.toList(), 1920, 1080)
    }

    @Test fun a_picture_is_drawn_without_resolving_a_font_and_carries_the_file_stamp() {
        val f = tmp.newFile("logo.png").apply { writeText("abc") }
        val o = TextOverlay("a", "", 0, 1000, image = OverlayImage(f.absolutePath, 10, 5))
        val layers = prepared(o)
        assertNotNull(layers); assertEquals(1, layers!!.size)
        assertEquals(f.length() xor (f.lastModified() shl 1), requests.single().imageStamp)
    }
    @Test fun a_missing_picture_file_fails_the_whole_preparation() {
        val o = TextOverlay("a", "", 0, 1000, image = OverlayImage(File(tmp.root, "gone.png").absolutePath, 10, 5))
        assertNull(prepared(o))
    }
}
