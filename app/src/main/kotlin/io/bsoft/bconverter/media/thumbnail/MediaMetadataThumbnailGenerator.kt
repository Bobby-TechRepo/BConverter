package io.bsoft.bconverter.media.thumbnail

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.bsoft.bconverter.domain.media.ThumbnailFailure
import io.bsoft.bconverter.domain.media.ThumbnailGenerator
import io.bsoft.bconverter.domain.media.ThumbnailResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Grabs one frame with the platform decoder (fast, hardware-backed) and saves it as a small JPEG. Corrupt media is a failure, not a crash. */
class MediaMetadataThumbnailGenerator(private val io: CoroutineDispatcher = Dispatchers.IO) : ThumbnailGenerator {
    override suspend fun generate(source: File, positionMs: Long, widthPx: Int, target: File): ThumbnailResult = withContext(io) {
        if (!source.isFile) return@withContext ThumbnailResult.Failure(ThumbnailFailure.SourceMissing)
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            retriever.setDataSource(source.absolutePath)
            frame = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@withContext ThumbnailResult.Failure(ThumbnailFailure.Unreadable)
            val width = widthPx.coerceAtLeast(1)
            val height = (frame.height * width.toFloat() / frame.width).toInt().coerceAtLeast(1)
            scaled = if (frame.width == width) frame else Bitmap.createScaledBitmap(frame, width, height, true)
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            ThumbnailResult.Success(target)
        } catch (e: IOException) {
            ThumbnailResult.Failure(ThumbnailFailure.Unknown)
        } catch (e: RuntimeException) {
            // MediaMetadataRetriever signals corrupt or unsupported media with runtime exceptions.
            ThumbnailResult.Failure(ThumbnailFailure.Unreadable)
        } finally {
            if (scaled != null && scaled !== frame) scaled.recycle()
            frame?.recycle()
            retriever.release()
        }
    }
}
