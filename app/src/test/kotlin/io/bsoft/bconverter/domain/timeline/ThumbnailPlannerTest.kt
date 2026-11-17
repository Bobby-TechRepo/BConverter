package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.model.MediaSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailPlannerTest {
    private fun clip(start: Long = 0, end: Long = 10_000, speed: Float = 1f, duration: Long = 60_000) =
        VideoClip("c", MediaSource.FileSource(File("/x.mp4")), duration, start, end, speed)

    @Test fun a_block_without_width_needs_no_thumbnails() {
        assertTrue(ThumbnailPlanner.plan(clip(), 0f, 40f).isEmpty())
        assertTrue(ThumbnailPlanner.plan(clip(), 100f, 0f).isEmpty())
    }

    @Test fun the_block_is_covered_by_tiles_the_last_one_shorter() {
        val tiles = ThumbnailPlanner.plan(clip(), 100f, 40f)
        assertEquals(listOf(0f, 40f, 80f), tiles.map { it.xPx })
        assertEquals(listOf(40f, 40f, 20f), tiles.map { it.widthPx })
        assertEquals(listOf(0, 1, 2), tiles.map { it.index })
    }

    @Test fun each_tile_shows_the_frame_at_its_centre() {
        val tiles = ThumbnailPlanner.plan(clip(0, 10_000), 100f, 50f)
        assertEquals(listOf(2_500L, 7_500L), tiles.map { it.positionMs })
    }

    @Test fun a_trimmed_clip_maps_into_its_source_range() {
        val tiles = ThumbnailPlanner.plan(clip(2_000, 6_000), 100f, 100f)
        assertEquals(listOf(4_000L), tiles.map { it.positionMs })
    }

    @Test fun speed_does_not_change_which_frames_are_shown() {
        assertEquals(
            ThumbnailPlanner.plan(clip(speed = 1f), 100f, 50f).map { it.positionMs },
            ThumbnailPlanner.plan(clip(speed = 2f), 100f, 50f).map { it.positionMs },
        )
    }

    @Test fun positions_are_rounded_down_to_a_bucket_so_nearby_tiles_share_a_frame() {
        val tiles = ThumbnailPlanner.plan(clip(0, 10_000), 1_000f, 10f)
        assertTrue(tiles.all { it.positionMs % ThumbnailPlanner.BUCKET_MS == 0L || it.positionMs == 0L })
        assertTrue(tiles.map { it.positionMs }.distinct().size < tiles.size)
    }

    @Test fun a_position_never_leaves_the_clip_range() {
        val c = clip(2_100, 2_900)
        ThumbnailPlanner.plan(c, 200f, 10f).forEach { assertTrue(it.positionMs in c.sourceStartMs until c.sourceEndMs) }
    }

    @Test fun a_very_long_block_is_capped_and_still_covered() {
        val tiles = ThumbnailPlanner.plan(clip(), 1_000_000f, 10f)
        assertEquals(ThumbnailPlanner.MAX_TILES, tiles.size)
        assertEquals(1_000_000f, tiles.last().xPx + tiles.last().widthPx, 0.5f)
    }

    // ---- visible ----
    private val tiles = ThumbnailPlanner.plan(clip(), 1_000f, 100f) // ten tiles of 100

    @Test fun only_tiles_in_the_viewport_plus_one_either_side_are_needed() {
        assertEquals((2..6).toList(), ThumbnailPlanner.visible(tiles, 350f, 550f).map { it.index })
    }

    @Test fun the_margin_is_clamped_at_the_ends() {
        assertEquals(listOf(0, 1), ThumbnailPlanner.visible(tiles, 0f, 50f).map { it.index })
        assertEquals(listOf(8, 9), ThumbnailPlanner.visible(tiles, 950f, 1_000f).map { it.index })
    }

    @Test fun a_viewport_outside_the_block_needs_nothing() {
        assertTrue(ThumbnailPlanner.visible(tiles, 5_000f, 5_400f, marginTiles = 0).isEmpty())
        assertTrue(ThumbnailPlanner.visible(emptyList(), 0f, 100f).isEmpty())
    }
}
