package io.bsoft.bconverter.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.OverlayRasterRequest
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.media.overlay.AndroidOverlayRasterizer
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Picture overlays are drawn to a transparent PNG at their size on the frame, like text overlays. */
@RunWith(AndroidJUnit4::class)
class AndroidOverlayPictureRasterizerTest {
    private val rasterizer = AndroidOverlayRasterizer()
    private val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private fun out() = File.createTempFile("overlay", ".png", cache)

    /** A 200x100 picture: left half opaque red, right half fully transparent. */
    private val source: File by lazy {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(0f, 0f, 100f, 100f, android.graphics.Paint().apply { color = Color.RED })
        File.createTempFile("source", ".png", cache).also { f -> FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun near(expected: Int, actual: Int, tolerance: Int) = assertTrue("expected $expected, got $actual", kotlin.math.abs(expected - actual) <= tolerance)
    private fun overlay(rot: Float = 0f, scale: Float = 1f, opacity: Float = 1f, path: String = source.path) =
        TextOverlay("p", "", 0, 1000, rotationDeg = rot, scale = scale, image = OverlayImage(path, 200, 100)).let { it.copy(style = it.style.copy(opacity = opacity)) }
    private fun draw(o: TextOverlay, f: File = out()) = rasterizer.render(OverlayRasterRequest(o, 1280, 720, null, 0), f)

    @Test fun the_picture_is_drawn_at_its_size_on_the_frame() {
        val f = out(); val r = draw(overlay(), f)!!
        assertEquals(432, r.width); assertEquals(216, r.height) // 0.3 of 720 tall, 2:1
        val b = BitmapFactory.decodeFile(f.path)
        assertEquals(r.width, b.width); assertEquals(r.height, b.height)
    }
    @Test fun transparency_is_kept() {
        val f = out(); draw(overlay(), f)
        val b = BitmapFactory.decodeFile(f.path)
        assertEquals(255, Color.alpha(b.getPixel(b.width / 4, b.height / 2)))
        assertEquals(0, Color.alpha(b.getPixel(b.width * 3 / 4, b.height / 2)))
        assertTrue(Color.red(b.getPixel(b.width / 4, b.height / 2)) > 200)
    }
    @Test fun scale_enlarges_the_picture() {
        val a = draw(overlay())!!; val b = draw(overlay(scale = 2f))!!
        near(a.width * 2, b.width, 2); near(a.height * 2, b.height, 2)
    }
    @Test fun a_quarter_turn_swaps_width_and_height() {
        val a = draw(overlay())!!; val b = draw(overlay(rot = 90f))!!
        near(a.width, b.height, 3); near(a.height, b.width, 3)
    }
    @Test fun opacity_is_baked_in() {
        val f = out(); draw(overlay(opacity = 0.5f), f)
        val b = BitmapFactory.decodeFile(f.path)
        near(128, Color.alpha(b.getPixel(b.width / 4, b.height / 2)), 3)
    }
    @Test fun a_missing_file_is_refused_not_crashed() { assertNull(draw(overlay(path = File(cache, "nope.png").path))) }
    @Test fun a_file_that_is_not_a_picture_is_refused() {
        val junk = File.createTempFile("junk", ".png", cache).apply { writeText("not a picture") }
        assertNull(draw(overlay(path = junk.path)))
    }
    @Test fun an_absurdly_large_picture_is_refused() { assertNull(draw(overlay(scale = 20f).let { it.copy(image = it.image!!.copy(heightFraction = 1f)) })) }
    @Test fun the_result_is_a_real_png_file() { val f = out(); assertNotNull(draw(overlay(), f)); assertTrue(f.length() > 0) }
}
