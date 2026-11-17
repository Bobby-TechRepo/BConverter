package io.bsoft.bconverter.media.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.bsoft.bconverter.domain.overlay.OverlayGeometry
import io.bsoft.bconverter.domain.overlay.OverlayRasterRequest
import io.bsoft.bconverter.domain.overlay.OverlayRasterizer
import io.bsoft.bconverter.domain.overlay.OverlayTextAlign
import io.bsoft.bconverter.domain.overlay.RasterizedOverlay
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a text overlay to a transparent PNG with the platform's text engine, laid out the way the editor's preview lays it out:
 * glow, outline, then the fill with its shadow, on an optional rounded box, at the overlay's size, wrap width, spacing and rotation.
 * The picture's centre is the overlay's centre, so FFmpeg only has to place it. Opacity is baked in; fades are done by FFmpeg.
 */
class AndroidOverlayRasterizer : OverlayRasterizer {
    override fun render(request: OverlayRasterRequest, out: File): RasterizedOverlay? {
        val o = request.overlay; val s = o.style
        if (o.isImage) return renderPicture(request, out)
        if (o.text.isBlank()) return null
        val px = OverlayGeometry.toPixels(o, request.canvasWidth, request.canvasHeight)
        val font = px.fontSizePx
        if (!font.isFinite() || font < 1f || font > MAX_FONT_PX) return null

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = font
            typeface = typeface(request.fontPath, s.fontWeight, s.italic)
            letterSpacing = s.letterSpacing
            isUnderlineText = s.underline
        }
        val wrapWidth = (px.boxWidthPx ?: request.canvasWidth.toFloat()).toInt().coerceAtLeast(1)
        val metrics = paint.fontMetrics
        val naturalLine = metrics.descent - metrics.ascent
        val lineHeight = font * LINE_HEIGHT * s.lineSpacing
        val align = when (s.align) {
            OverlayTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            OverlayTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            OverlayTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val measured = StaticLayout.Builder.obtain(o.text, 0, o.text.length, paint, wrapWidth).setAlignment(align)
            .setLineSpacing(lineHeight - naturalLine, 1f).setIncludePad(false).build()
        var textW = 0f
        for (i in 0 until measured.lineCount) textW = max(textW, measured.getLineWidth(i))
        // A box with a wrap width is as wide as the wrap width; otherwise it is as wide as its longest line.
        val layoutW = if (px.boxWidthPx != null) wrapWidth else ceil(textW).toInt().coerceAtLeast(1)
        val layout = if (layoutW == wrapWidth) measured else StaticLayout.Builder.obtain(o.text, 0, o.text.length, paint, layoutW)
            .setAlignment(align).setLineSpacing(lineHeight - naturalLine, 1f).setIncludePad(false).build()

        val pad = (s.background?.padding ?: 0f) * font
        val boxW = layoutW + 2 * pad; val boxH = layout.height + 2 * pad
        // Room around the box for whatever is drawn outside it (outline, glow, shadow), so nothing is cut off.
        val bleed = max(
            max((s.outline?.widthFraction ?: 0f) * font, (s.glow?.radius ?: 0f) * font * 2f),
            s.shadow?.let { max(abs(it.dx), abs(it.dy)) * font + it.blur * font * 2f } ?: 0f,
        )
        val contentW = boxW + 2 * bleed; val contentH = boxH + 2 * bleed
        val rad = Math.toRadians(o.rotationDeg.toDouble())
        val w = ceil(abs(contentW * cos(rad)) + abs(contentH * sin(rad))).toInt().coerceAtLeast(1)
        val h = ceil(abs(contentW * sin(rad)) + abs(contentH * cos(rad))).toInt().coerceAtLeast(1)
        if (w.toLong() * h > MAX_PIXELS || w > MAX_SIDE || h > MAX_SIDE) return null

        val bitmap = try { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) } catch (e: OutOfMemoryError) { return null }
        try {
            val canvas = Canvas(bitmap)
            canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (s.opacity * 255).toInt().coerceIn(0, 255))
            canvas.translate(w / 2f, h / 2f)
            canvas.rotate(o.rotationDeg)
            canvas.translate(-boxW / 2f, -boxH / 2f)
            s.background?.let { b ->
                val r = b.cornerRadius * font
                canvas.drawRoundRect(RectF(0f, 0f, boxW, boxH), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.color })
            }
            canvas.translate(pad, pad)
            s.glow?.takeIf { it.radius > 0f }?.let { g ->
                paint.style = Paint.Style.FILL; paint.color = g.color; paint.clearShadowLayer()
                paint.maskFilter = BlurMaskFilter((g.radius * font).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                layout.draw(canvas); paint.maskFilter = null
            }
            s.outline?.takeIf { it.widthFraction > 0f }?.let { ol ->
                paint.style = Paint.Style.STROKE; paint.strokeWidth = ol.widthFraction * font * 2f; paint.strokeJoin = Paint.Join.ROUND
                paint.color = ol.color; paint.clearShadowLayer()
                layout.draw(canvas)
            }
            paint.style = Paint.Style.FILL; paint.color = s.color
            s.shadow?.let { sh -> paint.setShadowLayer((sh.blur * font).coerceAtLeast(0.01f), sh.dx * font, sh.dy * font, sh.color) } ?: paint.clearShadowLayer()
            layout.draw(canvas)
            canvas.restore()
            FileOutputStream(out).use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) return null }
        } finally {
            bitmap.recycle()
        }
        return RasterizedOverlay(w, h)
    }

    /** A picture overlay: the file scaled to its size on the frame, rotated, with the overlay's opacity; transparency is kept. */
    private fun renderPicture(request: OverlayRasterRequest, out: File): RasterizedOverlay? {
        val o = request.overlay
        val image = o.image ?: return null
        val file = File(image.path).takeIf { it.isFile } ?: return null
        val (pw, ph) = OverlayGeometry.imageSizePx(o, request.canvasWidth, request.canvasHeight)
        if (!pw.isFinite() || !ph.isFinite() || pw < 1f || ph < 1f) return null
        val rad = Math.toRadians(o.rotationDeg.toDouble())
        // A tiny allowance so float noise (432.00003) does not add a whole extra pixel.
        val w = ceil(abs(pw * cos(rad)) + abs(ph * sin(rad)) - 0.01).toInt().coerceAtLeast(1)
        val h = ceil(abs(pw * sin(rad)) + abs(ph * cos(rad)) - 0.01).toInt().coerceAtLeast(1)
        if (w.toLong() * h > MAX_PIXELS || w > MAX_SIDE || h > MAX_SIDE) return null

        // Decode no larger than needed: the file is at most 2048 px, but the picture on the frame may be much smaller.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= pw && bounds.outHeight / (sample * 2) >= ph) sample *= 2
        val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        val bitmap = try { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) } catch (e: OutOfMemoryError) { source.recycle(); return null }
        try {
            val canvas = Canvas(bitmap)
            canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (o.style.opacity * 255).toInt().coerceIn(0, 255))
            canvas.translate(w / 2f, h / 2f)
            canvas.rotate(o.rotationDeg)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(source, null, RectF(-pw / 2f, -ph / 2f, pw / 2f, ph / 2f), paint)
            canvas.restore()
            FileOutputStream(out).use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) return null }
        } finally {
            source.recycle(); bitmap.recycle()
        }
        return RasterizedOverlay(w, h)
    }

    private fun typeface(path: String?, weight: Int, italic: Boolean): Typeface {
        path?.let { p -> try { return Typeface.createFromFile(p) } catch (e: Exception) { /* fall through to the default font */ } }
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, italic)
        else Typeface.create(Typeface.DEFAULT, when { weight >= 600 && italic -> Typeface.BOLD_ITALIC; weight >= 600 -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
    }

    private companion object {
        const val LINE_HEIGHT = 1.2f
        const val MAX_FONT_PX = 3000f
        const val MAX_SIDE = 8192
        const val MAX_PIXELS = 24L * 1024 * 1024
    }
}
