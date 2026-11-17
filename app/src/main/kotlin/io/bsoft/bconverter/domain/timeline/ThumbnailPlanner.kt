package io.bsoft.bconverter.domain.timeline

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One thumbnail slot on a video clip block: where it sits (block-relative px) and which source frame it shows. */
data class ThumbnailTile(val index: Int, val xPx: Float, val widthPx: Float, val positionMs: Long)

/** Decides which frames a clip block needs (spec §27: only the required thumbnails). Pure. */
object ThumbnailPlanner {
    const val BUCKET_MS = 500L
    const val MAX_TILES = 400

    fun plan(clip: VideoClip, blockWidthPx: Float, tileWidthPx: Float, bucketMs: Long = BUCKET_MS): List<ThumbnailTile> {
        if (blockWidthPx <= 0f || tileWidthPx <= 0f) return emptyList()
        val count = min(ceil(blockWidthPx / tileWidthPx).toInt(), MAX_TILES)
        val tile = if (count == MAX_TILES) blockWidthPx / count else tileWidthPx
        val span = clip.sourceEndMs - clip.sourceStartMs
        return (0 until count).map { i ->
            val x = i * tile
            val w = min(tile, blockWidthPx - x)
            val fraction = ((x + w / 2f) / blockWidthPx).coerceIn(0f, 1f)
            val raw = clip.sourceStartMs + (fraction * span).toLong()
            val bucketed = raw / bucketMs * bucketMs
            ThumbnailTile(i, x, w, bucketed.coerceIn(clip.sourceStartMs, clip.sourceEndMs - 1))
        }
    }

    /** Tiles intersecting [fromPx, toPx] plus [marginTiles] on each side, so scrolling does not show blanks. */
    fun visible(tiles: List<ThumbnailTile>, fromPx: Float, toPx: Float, marginTiles: Int = 1): List<ThumbnailTile> {
        val inside = tiles.indices.filter { tiles[it].xPx < toPx && tiles[it].xPx + tiles[it].widthPx > fromPx }
        if (inside.isEmpty()) return emptyList()
        return tiles.subList(max(inside.first() - marginTiles, 0), min(inside.last() + marginTiles + 1, tiles.size))
    }
}
