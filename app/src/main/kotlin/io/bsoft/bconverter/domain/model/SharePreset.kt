package io.bsoft.bconverter.domain.model

import io.bsoft.bconverter.domain.canvas.CanvasAspect

/**
 * One tap in the export dialog that sets the picture shape, the size and the quality for a place the video is going to.
 * The picture fit and background the user chose are left alone.
 */
enum class SharePreset(val aspect: CanvasAspect, val size: ExportSize, val quality: ExportPreset) {
    YOUTUBE(CanvasAspect.WIDE, ExportSize.P1080, ExportPreset.BALANCED),
    VERTICAL_SHORTS(CanvasAspect.TALL, ExportSize.P1080, ExportPreset.BALANCED),
    INSTAGRAM_POST(CanvasAspect.PORTRAIT, ExportSize.P1080, ExportPreset.BALANCED),
    SQUARE(CanvasAspect.SQUARE, ExportSize.P1080, ExportPreset.BALANCED),
    WHATSAPP_STATUS(CanvasAspect.TALL, ExportSize.P720, ExportPreset.SMALL_FILE);

    companion object {
        /** The preset the current choices amount to, or null when they match none (so the dialog can show it as picked). */
        fun matching(aspect: CanvasAspect, size: ExportSize, quality: ExportPreset): SharePreset? =
            entries.firstOrNull { it.aspect == aspect && it.size == size && it.quality == quality }
    }
}
