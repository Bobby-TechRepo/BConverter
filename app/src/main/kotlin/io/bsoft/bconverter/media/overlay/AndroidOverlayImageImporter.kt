package io.bsoft.bconverter.media.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import io.bsoft.bconverter.domain.overlay.ImageImport
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.OverlayImageImporter
import io.bsoft.bconverter.domain.overlay.OverlayImageSizing
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Copies a chosen picture (PNG, JPEG, WebP; transparency kept) into [dir] as a PNG: shrunk to [OverlayImageSizing.MAX_SIDE] on the
 * longest side and turned upright if the file says it is rotated. The project then refers to this private copy, so it keeps working
 * if the original is moved or deleted.
 */
class AndroidOverlayImageImporter(
    private val context: Context,
    private val dir: File,
    private val dispatcher: CoroutineDispatcher,
) : OverlayImageImporter {
    override suspend fun import(uri: String, frameAspect: Float): ImageImport = withContext(dispatcher) {
        val source = Uri.parse(uri)
        val resolver = context.contentResolver
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Reading only the size returns no bitmap by design, so a null result here means nothing; the size is what counts.
            val stream = resolver.openInputStream(source) ?: return@withContext ImageImport.Unreadable
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext ImageImport.Unreadable
            if (!OverlayImageSizing.isAcceptable(bounds.outWidth, bounds.outHeight)) return@withContext ImageImport.TooLarge

            val options = BitmapFactory.Options().apply {
                inSampleSize = OverlayImageSizing.sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext ImageImport.Unreadable
            val orientation = try {
                resolver.openInputStream(source)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
            bitmap = upright(bitmap, orientation)
            val (w, h) = OverlayImageSizing.fit(bitmap.width, bitmap.height)
            if (w != bitmap.width || h != bitmap.height) bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)

            dir.mkdirs()
            val file = File(dir, "img-${UUID.randomUUID()}.png")
            val written = FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val (bw, bh) = bitmap.width to bitmap.height
            bitmap.recycle()
            if (!written || file.length() == 0L) { file.delete(); return@withContext ImageImport.Unreadable }
            ImageImport.Imported(OverlayImage(file.absolutePath, bw, bh, OverlayImageSizing.defaultHeightFraction(bw.toFloat() / bh, frameAspect)))
        } catch (e: OutOfMemoryError) {
            ImageImport.TooLarge
        } catch (e: Exception) {
            ImageImport.Unreadable
        }
    }

    private fun upright(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }
}
