package io.bsoft.bconverter.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.domain.overlay.ImageImport
import io.bsoft.bconverter.media.overlay.AndroidOverlayImageImporter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidOverlayImageImporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "imported-images").apply { deleteRecursively() }
    private val importer = AndroidOverlayImageImporter(context, dir, Dispatchers.IO)

    private fun picture(w: Int, h: Int, format: Bitmap.CompressFormat, name: String): Uri {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(0f, 0f, w / 2f, h.toFloat(), Paint().apply { color = Color.RED })
        val f = File(context.cacheDir, name)
        FileOutputStream(f).use { b.compress(format, 100, it) }
        return Uri.fromFile(f)
    }
    private fun import(uri: Uri, aspect: Float = 16f / 9f) = runBlocking { importer.import(uri.toString(), aspect) }

    @Test fun a_png_is_copied_into_the_app_with_its_size_and_transparency() {
        val r = import(picture(400, 200, Bitmap.CompressFormat.PNG, "a.png")) as ImageImport.Imported
        assertEquals(400, r.image.widthPx); assertEquals(200, r.image.heightPx)
        val copy = File(r.image.path)
        assertTrue(copy.isFile && copy.parentFile == dir)
        val b = BitmapFactory.decodeFile(copy.path)
        assertEquals(0, Color.alpha(b.getPixel(300, 100))); assertEquals(255, Color.alpha(b.getPixel(100, 100)))
    }
    @Test fun a_jpeg_and_a_webp_are_accepted() {
        assertTrue(import(picture(300, 300, Bitmap.CompressFormat.JPEG, "a.jpg")) is ImageImport.Imported)
        assertTrue(import(picture(300, 300, Bitmap.CompressFormat.WEBP_LOSSLESS, "a.webp")) is ImageImport.Imported)
    }
    @Test fun a_big_picture_is_shrunk_to_2048_on_the_longest_side() {
        val r = import(picture(4096, 2048, Bitmap.CompressFormat.PNG, "big.png")) as ImageImport.Imported
        assertEquals(2048, r.image.widthPx); assertEquals(1024, r.image.heightPx)
    }
    @Test fun a_wide_banner_starts_no_wider_than_half_the_frame() {
        val r = import(picture(1200, 200, Bitmap.CompressFormat.PNG, "banner.png")) as ImageImport.Imported
        assertTrue(r.image.heightFraction * r.image.aspect <= 0.5f * (16f / 9f) + 0.001f)
    }
    @Test fun the_copy_survives_the_original_being_deleted() {
        val uri = picture(100, 100, Bitmap.CompressFormat.PNG, "gone.png")
        val r = import(uri) as ImageImport.Imported
        File(uri.path!!).delete()
        assertTrue(File(r.image.path).isFile)
    }
    @Test fun something_that_is_not_a_picture_is_unreadable() {
        val f = File(context.cacheDir, "notes.png").apply { writeText("hello") }
        assertEquals(ImageImport.Unreadable, import(Uri.fromFile(f)))
    }
    @Test fun a_missing_file_is_unreadable() { assertEquals(ImageImport.Unreadable, import(Uri.fromFile(File(context.cacheDir, "missing.png")))) }
}
