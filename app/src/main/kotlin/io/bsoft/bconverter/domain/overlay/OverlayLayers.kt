package io.bsoft.bconverter.domain.overlay

import io.bsoft.bconverter.domain.font.FontLibrary
import java.io.File
import java.security.MessageDigest

/** What the rasterizer needs to draw one overlay as a transparent picture at the export's frame size. */
data class OverlayRasterRequest(
    val overlay: TextOverlay,
    val canvasWidth: Int,
    val canvasHeight: Int,
    /** The font file chosen for the overlay's family and weight; null draws with the default font. */
    val fontPath: String?,
    /** Changes when the font file changes (length and modified time), so a replaced file never reuses an old picture. */
    val fontStamp: Long,
    /** For a picture overlay: changes when the picture file changes (length and modified time). */
    val imageStamp: Long = 0,
) {
    init { require(canvasWidth > 0 && canvasHeight > 0) { "canvas size" } }
}

/** The size of a picture the rasterizer wrote. The overlay's centre is the picture's centre. */
data class RasterizedOverlay(val width: Int, val height: Int)

/** Draws text overlays to transparent PNG files. The Android implementation uses the platform's text engine. */
interface OverlayRasterizer {
    /** Writes [out] and returns its size, or null if the overlay cannot be drawn (e.g. it would be absurdly large). */
    fun render(request: OverlayRasterRequest, out: File): RasterizedOverlay?
}

/** A drawn overlay ready for FFmpeg: [file] is a PNG of [width]x[height] centred on the overlay's position. */
data class OverlayLayer(val overlay: TextOverlay, val file: File, val width: Int, val height: Int)

/** Prepares the pictures for the overlays of one export. Null means at least one overlay could not be drawn. */
interface OverlayLayerPreparer {
    suspend fun prepare(overlays: List<TextOverlay>, canvasWidth: Int, canvasHeight: Int): List<OverlayLayer>?
}

object OverlayLayerKey {
    /** Bump when the rasterizer's look changes, so old cached pictures are not reused. */
    const val RENDERER_VERSION = 1

    /**
     * A name for the picture of this request. Only what changes the picture counts: text, look, size, rotation, wrap width,
     * frame size and font. Where and when the overlay shows, and how it animates, do not (FFmpeg does that), so moving or
     * re-timing an overlay reuses the cached picture.
     */
    fun of(r: OverlayRasterRequest): String {
        val o = r.overlay; val s = o.style
        val text = listOf(
            RENDERER_VERSION, o.text, o.scale, o.rotationDeg, o.widthFraction, r.canvasWidth, r.canvasHeight, r.fontPath, r.fontStamp,
            s.fontFamily, s.fontWeight, s.italic, s.underline, s.sizeFraction, s.color, s.opacity, s.align, s.letterSpacing, s.lineSpacing,
            s.outline, s.shadow, s.glow, s.background,
            // A picture is keyed by its file, stamp and height; a text overlay has none of these, so the two never collide.
            o.image?.path, o.image?.heightFraction, r.imageStamp,
        ).joinToString("\u0001")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }
}

/**
 * Keeps drawn overlays on disk, named `ol-<key>-<width>x<height>.png`, and reuses them across exports. When the folder grows
 * past [maxBytes] the least recently used pictures are deleted.
 */
class OverlayLayerCache(
    private val dir: File,
    private val rasterizer: OverlayRasterizer,
    private val maxBytes: Long = 50L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val nameRx = Regex("^ol-([0-9a-f]+)-(\\d+)x(\\d+)\\.png$")

    /** The picture for [request]: from the cache if it is there, otherwise drawn now. Null if it cannot be drawn. */
    @Synchronized
    fun layerFor(request: OverlayRasterRequest): CachedLayer? {
        val key = OverlayLayerKey.of(request)
        existing(key)?.let { (file, w, h) -> file.setLastModified(clock()); return CachedLayer(file, w, h) }
        dir.mkdirs()
        val tmp = File(dir, "ol-$key.part")
        val size = try { rasterizer.render(request, tmp) } catch (e: Exception) {
            io.bsoft.bconverter.core.diagnostics.AppLog.nonFatal("Overlay", "Rendering an overlay picture failed", e); null
        }
        if (size == null || !tmp.isFile || tmp.length() == 0L) { tmp.delete(); return null }
        val target = File(dir, "ol-$key-${size.width}x${size.height}.png")
        if (!tmp.renameTo(target)) { tmp.delete(); return null }
        target.setLastModified(clock())
        return CachedLayer(target, size.width, size.height)
    }

    private fun existing(key: String): Triple<File, Int, Int>? = dir.listFiles().orEmpty().firstNotNullOfOrNull { f ->
        nameRx.matchEntire(f.name)?.takeIf { it.groupValues[1] == key && f.length() > 0 }?.let { Triple(f, it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
    }

    /** Deletes the least recently used pictures until the folder is within the size limit; [keep] files are never deleted. */
    @Synchronized
    fun trim(keep: Set<File> = emptySet()) {
        val files = dir.listFiles().orEmpty().filter { nameRx.matches(it.name) || it.name.endsWith(".part") }
        var total = files.sumOf { it.length() }
        files.filter { it.name.endsWith(".part") && it !in keep }.forEach { total -= it.length(); it.delete() }
        files.filter { it.exists() && it !in keep }.sortedBy { it.lastModified() }.forEach { if (total > maxBytes) { total -= it.length(); it.delete() } }
    }

    fun clear() { dir.listFiles().orEmpty().forEach { it.delete() } }
}

data class CachedLayer(val file: File, val width: Int, val height: Int)

/** Resolves each overlay's font, draws it through the cache, and trims the cache afterwards. */
class CachedOverlayLayerPreparer(private val cache: OverlayLayerCache, private val fonts: FontLibrary) : OverlayLayerPreparer {
    override suspend fun prepare(overlays: List<TextOverlay>, canvasWidth: Int, canvasHeight: Int): List<OverlayLayer>? {
        val layers = ArrayList<OverlayLayer>(overlays.size)
        for (o in overlays) {
            val image = o.image
            if (image != null) {
                val file = File(image.path).takeIf { it.isFile } ?: return null
                val request = OverlayRasterRequest(o, canvasWidth, canvasHeight, null, 0L, imageStamp = file.length() xor (file.lastModified() shl 1))
                val cached = cache.layerFor(request) ?: return null
                layers += OverlayLayer(o, cached.file, cached.width, cached.height)
                continue
            }
            val font = fonts.resolve(o.style.fontFamily, o.style.fontWeight, o.style.italic).file?.let { File(it.path) }?.takeIf { it.isFile }
            val request = OverlayRasterRequest(o, canvasWidth, canvasHeight, font?.path, font?.let { it.length() xor (it.lastModified() shl 1) } ?: 0L)
            val cached = cache.layerFor(request) ?: return null
            layers += OverlayLayer(o, cached.file, cached.width, cached.height)
        }
        cache.trim(keep = layers.map { it.file }.toSet())
        return layers
    }
}
