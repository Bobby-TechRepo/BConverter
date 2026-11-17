package io.bsoft.bconverter.media

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.domain.overlay.OverlayBackground
import io.bsoft.bconverter.domain.overlay.OverlayGlow
import io.bsoft.bconverter.domain.overlay.OverlayOutline
import io.bsoft.bconverter.domain.overlay.OverlayRasterRequest
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.overlay.TextOverlayStyle
import io.bsoft.bconverter.media.overlay.AndroidOverlayRasterizer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidOverlayRasterizerTest {
    private val rasterizer = AndroidOverlayRasterizer()
    private fun file() = File.createTempFile("overlay", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private fun req(o: TextOverlay) = OverlayRasterRequest(o, 1280, 720, null, 0)
    private fun overlay(text: String = "Hello", style: TextOverlayStyle = TextOverlayStyle(), rot: Float = 0f, width: Float? = null, scale: Float = 1f) =
        TextOverlay("a", text, 0, 1000, rotationDeg = rot, style = style, widthFraction = width, scale = scale)

    @Test fun draws_a_transparent_png_of_the_reported_size() {
        val f = file(); val r = rasterizer.render(req(overlay()), f)!!
        val b = BitmapFactory.decodeFile(f.path)
        assertEquals(r.width, b.width); assertEquals(r.height, b.height); assertTrue(b.hasAlpha())
        assertEquals(0, b.getPixel(0, 0) ushr 24) // the corner is transparent
        var opaque = 0; for (x in 0 until b.width) for (y in 0 until b.height) if ((b.getPixel(x, y) ushr 24) > 200) opaque++
        assertTrue(opaque > 50)
    }
    @Test fun blank_text_is_not_drawn() = assertNull(rasterizer.render(req(overlay("  ")), file()))
    @Test fun bigger_scale_gives_a_bigger_picture() {
        val a = rasterizer.render(req(overlay()), file())!!; val b = rasterizer.render(req(overlay(scale = 2f)), file())!!
        assertTrue(b.width > a.width * 3 / 2 && b.height > a.height * 3 / 2)
    }
    @Test fun a_quarter_turn_swaps_width_and_height_roughly() {
        val a = rasterizer.render(req(overlay("Long wide text here")), file())!!; val b = rasterizer.render(req(overlay("Long wide text here", rot = 90f)), file())!!
        assertEquals(a.width.toFloat(), b.height.toFloat(), 3f); assertEquals(a.height.toFloat(), b.width.toFloat(), 3f)
    }
    @Test fun a_wrap_width_makes_taller_text() {
        val text = "one two three four five six seven eight nine ten"
        val flat = rasterizer.render(req(overlay(text)), file())!!; val wrapped = rasterizer.render(req(overlay(text, width = 0.3f)), file())!!
        assertTrue(wrapped.height > flat.height); assertTrue(wrapped.width < flat.width)
    }
    @Test fun outline_glow_and_background_enlarge_the_picture() {
        val plain = rasterizer.render(req(overlay()), file())!!
        val rich = rasterizer.render(req(overlay(style = TextOverlayStyle(outline = OverlayOutline(), glow = OverlayGlow(), background = OverlayBackground()))), file())!!
        assertTrue(rich.width > plain.width && rich.height > plain.height)
    }
    @Test fun absurdly_large_overlays_are_refused_not_crashed() {
        assertNull(rasterizer.render(req(overlay(scale = 20f, style = TextOverlayStyle(sizeFraction = 1f))), file()))
    }
    @Test fun opacity_is_baked_into_the_picture() {
        val f = file(); rasterizer.render(req(overlay("MMMM", TextOverlayStyle(opacity = 0.5f, sizeFraction = 0.2f))), f)
        val b = BitmapFactory.decodeFile(f.path); var max = 0
        for (x in 0 until b.width) for (y in 0 until b.height) max = maxOf(max, b.getPixel(x, y) ushr 24)
        assertNotNull(b); assertTrue("max alpha $max", max in 100..140)
    }
}
