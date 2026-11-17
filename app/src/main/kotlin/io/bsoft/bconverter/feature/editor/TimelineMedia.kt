package io.bsoft.bconverter.feature.editor

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.data.storage.DiskThumbnailStore
import io.bsoft.bconverter.domain.media.LruMemoryCache
import io.bsoft.bconverter.domain.media.ThumbnailResult
import io.bsoft.bconverter.domain.media.ThumbnailService
import io.bsoft.bconverter.domain.media.Waveform
import io.bsoft.bconverter.domain.media.WaveformResult
import io.bsoft.bconverter.domain.media.WaveformService
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the timeline needs to decorate clips. Both calls return null when nothing can be shown; the clip then simply stays plain. */
interface TimelineMedia {
    suspend fun frame(file: File, positionMs: Long, widthPx: Int): ImageBitmap?
    suspend fun waveform(file: File): Waveform?
}

class ServiceTimelineMedia(private val thumbnails: ThumbnailService, private val waveforms: WaveformService) : TimelineMedia {
    // Tiles scrolled out of view and back are shown again without decoding the JPEG once more; the budget keeps memory bounded.
    private val frames = LruMemoryCache<String, ImageBitmap>(FRAME_CACHE_BYTES) { it.width.toLong() * it.height * 4 }
    private val waves = LruMemoryCache<String, Waveform>(WAVEFORM_CACHE_BYTES) { it.peaks.size.toLong() * 16 + 64 }

    override suspend fun frame(file: File, positionMs: Long, widthPx: Int): ImageBitmap? {
        val key = "${file.path}|${file.lastModified()}|$positionMs|$widthPx"
        frames.get(key)?.let { return it }
        val result = thumbnails.thumbnail(file, positionMs, widthPx) as? ThumbnailResult.Success ?: return null
        val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.file.path)?.asImageBitmap() } ?: return null
        frames.put(key, bitmap)
        return bitmap
    }

    override suspend fun waveform(file: File): Waveform? {
        val key = "${file.path}|${file.lastModified()}|${file.length()}"
        waves.get(key)?.let { return it }
        val wave = (waveforms.waveform(file) as? WaveformResult.Success)?.waveform ?: return null
        waves.put(key, wave)
        return wave
    }

    private companion object {
        const val FRAME_CACHE_BYTES = 24L * 1024 * 1024
        const val WAVEFORM_CACHE_BYTES = 4L * 1024 * 1024
    }
}

@HiltViewModel
class TimelineMediaViewModel @Inject constructor(
    thumbnails: ThumbnailService,
    waveforms: WaveformService,
    store: DiskThumbnailStore,
) : ViewModel() {
    val media: TimelineMedia = ServiceTimelineMedia(thumbnails, waveforms)

    init { viewModelScope.launch(Dispatchers.IO) { store.trim() } }
}
