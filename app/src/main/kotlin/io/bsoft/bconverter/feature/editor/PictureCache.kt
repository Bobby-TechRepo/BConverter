package io.bsoft.bconverter.feature.editor

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.util.LruCache
import java.io.File

/** Decoded pictures for the preview, by file and size; a small least-recently-used cache so dragging never decodes again. */
internal object PictureCache {
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun load(path: String, targetWidth: Int, targetHeight: Int): ImageBitmap? {
        val file = File(path).takeIf { it.isFile } ?: return null
        val key = "$path|${file.length()}|${file.lastModified()}|${bucket(targetWidth)}x${bucket(targetHeight)}"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth && bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
        val bitmap = try { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) } catch (e: OutOfMemoryError) { null } ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    /** Sizes are grouped so a pinch gesture does not decode a new copy for every pixel of growth. */
    private fun bucket(v: Int) = (v + 63) / 64 * 64
    private const val MAX_BYTES = 24 * 1024 * 1024
}
