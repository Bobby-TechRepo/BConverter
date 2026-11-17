package io.bsoft.bconverter.media.transformer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.render.HardwareRender
import io.bsoft.bconverter.media.api.MediaProcessingResult
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/** Renders a [HardwareRender.Plan] (step 19). Cancelling the calling coroutine cancels the export. */
fun interface HardwareRenderer {
    suspend fun render(plan: HardwareRender.Plan, output: File, onProgress: (Float) -> Unit): MediaProcessingResult
}

/**
 * [HardwareRenderer] on Media3 Transformer: the clips in one sequence, each trimmed, then scaled into the canvas with
 * black bars. Encoder fallback is off, so a device that cannot encode the requested format fails (and FFmpeg takes
 * over) instead of quietly writing something else.
 */
@OptIn(UnstableApi::class)
class Media3ProjectRenderer(private val context: Context) : HardwareRenderer {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override suspend fun render(plan: HardwareRender.Plan, output: File, onProgress: (Float) -> Unit): MediaProcessingResult =
        // Transformer is driven from the thread it was built on; the main looper is always available.
        withContext(Dispatchers.Main) {
            coroutineScope {
                val transformer = build(plan)
                val poller = launch {
                    val holder = ProgressHolder()
                    while (true) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        delay(PROGRESS_INTERVAL_MS)
                    }
                }
                try {
                    run(transformer, composition(plan), output, wantedMime(plan.videoCodec))
                } finally {
                    poller.cancel()
                }
            }
        }

    private fun build(plan: HardwareRender.Plan): Transformer {
        val encoderSettings = VideoEncoderSettings.Builder().apply { plan.videoBitrateKbps?.let { setBitrate(it * 1000) } }.build()
        return Transformer.Builder(context)
            .setVideoMimeType(wantedMime(plan.videoCodec))
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(encoderSettings).setEnableFallback(false).build(),
            )
            .build()
    }

    private fun composition(plan: HardwareRender.Plan): Composition {
        val items = plan.clips.map { clip ->
            val media = MediaItem.Builder()
                .setUri(Uri.fromFile(clip.file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build(),
                )
                .build()
            EditedMediaItem.Builder(media).setRemoveAudio(!plan.keepSound).build()
        }
        val canvas = Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT)
        return Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .setEffects(Effects(emptyList(), listOf(canvas)))
            .build()
    }

    private suspend fun run(transformer: Transformer, composition: Composition, output: File, wantedMime: String): MediaProcessingResult =
        suspendCancellableCoroutine { cont ->
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val result = when {
                        exportResult.videoMimeType != null && exportResult.videoMimeType != wantedMime ->
                            MediaProcessingResult.Failure("Hardware export wrote ${exportResult.videoMimeType} instead of $wantedMime")
                        output.exists() && output.length() > 0 -> MediaProcessingResult.Success(output)
                        else -> MediaProcessingResult.Failure("Hardware export produced no output file")
                    }
                    if (cont.isActive) cont.resume(result)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.resume(MediaProcessingResult.Failure("Hardware export failed: ${exportException.errorCodeName}: ${exportException.message}"))
                }
            })
            // Cancellation can arrive on any thread; Transformer must be called on the one it was built on.
            cont.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
            transformer.start(composition, output.absolutePath)
        }

    private fun wantedMime(codec: VideoCodec): String = if (codec == VideoCodec.HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264

    private companion object {
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
