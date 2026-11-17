package io.bsoft.bconverter.domain.transform

/** Turns a [ClipTransform] into FFmpeg filters. `crop`, `transpose`, `hflip` and `vflip` are core FFmpeg filters. */
object ClipTransformFilters {
    /** Every FFmpeg filter this file may emit; the device test checks each is in the device's own filter list. */
    val USED_FILTERS = listOf("crop", "transpose", "hflip", "vflip")

    /** Filters for an upright source of [width] × [height] (the picture after any rotation flag in the file has been applied). */
    fun build(t: ClipTransform, width: Int, height: Int): List<String> {
        val out = mutableListOf<String>()
        val c = t.crop
        if (!c.isFull) {
            val w = ClipTransform.croppedSide(width, c.left, c.right)
            val h = ClipTransform.croppedSide(height, c.top, c.bottom)
            out += "crop=$w:$h:${ClipTransform.cropOffset(width, c.left)}:${ClipTransform.cropOffset(height, c.top)}"
        }
        when (t.rotationDegrees) {
            90 -> out += "transpose=1"
            180 -> { out += "hflip"; out += "vflip" }
            270 -> out += "transpose=2"
        }
        if (t.flipHorizontal) out += "hflip"
        if (t.flipVertical) out += "vflip"
        return out
    }
}
