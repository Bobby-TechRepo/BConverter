package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyTest {
    private val key = ChromaKey(0x00FF00, similarity = 0.2f, blend = 0.2f)
    /** FFmpeg's own `chromakey` alpha (0..255) for solid colours, measured with the same settings. */
    private fun a(rgb: Int) = Math.round(ChromaKeyMath.alpha((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255, key) * 255)

    @Test fun the_key_colour_itself_is_removed() { assertEquals(0, a(0x00FF00)) }
    @Test fun a_slightly_different_green_is_removed_too() { assertEquals(0, a(0x20E020)) }
    @Test fun colours_match_what_ffmpeg_measured() {
        assertEquals(14.0, a(0x40C040).toDouble(), 4.0)
        assertEquals(39.0, a(0x60D060).toDouble(), 6.0)
        assertEquals(225.0, a(0x808080).toDouble(), 6.0)
        assertEquals(176.0, a(0x00FFFF).toDouble(), 10.0)
    }
    @Test fun far_colours_stay_fully_opaque() { assertEquals(255, a(0xFF0000)); assertEquals(255, a(0x0000FF)); assertEquals(255, a(0xFF8000)) }
    @Test fun brightness_alone_does_not_matter_much() { assertEquals(0, a(0x00A000)) }
    @Test fun a_blue_key_removes_blue_not_green() {
        val blue = ChromaKey(0x0000FF)
        assertEquals(0f, ChromaKeyMath.alpha(0, 0, 255, blue), 0f); assertEquals(1f, ChromaKeyMath.alpha(0, 255, 0, blue), 0f)
    }
    @Test fun without_a_soft_edge_it_is_all_or_nothing() {
        val hard = ChromaKey(0x00FF00, 0.2f, 0f)
        assertEquals(0f, ChromaKeyMath.alpha(0x20, 0xE0, 0x20, hard), 0f); assertEquals(1f, ChromaKeyMath.alpha(255, 0, 0, hard), 0f)
    }
    @Test fun a_wider_similarity_removes_more() {
        val narrow = ChromaKey(0x00FF00, 0.05f, 0f); val wide = ChromaKey(0x00FF00, 0.4f, 0f)
        assertEquals(1f, ChromaKeyMath.alpha(0x40, 0xC0, 0x40, narrow), 0f); assertEquals(0f, ChromaKeyMath.alpha(0x40, 0xC0, 0x40, wide), 0f)
    }
    @Test fun applying_it_to_pixels_changes_only_the_alpha() {
        val out = ChromaKeyMath.apply(intArrayOf(0xFF00FF00.toInt(), 0xFFFF0000.toInt(), 0x80FF0000.toInt()), ChromaKey(0x00FF00, 0.2f, 0.1f))
        assertEquals(0x0000FF00, out[0]); assertEquals(0xFFFF0000.toInt(), out[1]); assertEquals(0x80FF0000.toInt(), out[2])
    }
    @Test fun settings_outside_their_range_are_not_a_key() {
        assertTrue(runCatching { ChromaKey(similarity = 0f) }.isFailure); assertTrue(runCatching { ChromaKey(similarity = 0.6f) }.isFailure)
        assertTrue(runCatching { ChromaKey(blend = -0.1f) }.isFailure); assertTrue(runCatching { ChromaKey(rgb = -1) }.isFailure)
    }

    private val withPip = ok(TimelineOperations.addPip(TL.timeline(TL.video("a", 10_000)), PipClip("p", TL.source("p"), 6_000, 0, 4_000, 1_000)))
    @Test fun a_key_can_be_set_and_cleared() {
        val keyed = ok(TimelineOperations.setPipChromaKey(withPip, "p", ChromaKey()))
        assertEquals(ChromaKey(), keyed.pipClips.single().chromaKey)
        assertEquals(null, ok(TimelineOperations.setPipChromaKey(keyed, "p", null)).pipClips.single().chromaKey)
    }
    @Test fun the_key_of_an_unknown_clip_is_refused() { assertTrue(TimelineOperations.setPipChromaKey(withPip, "zz", ChromaKey()) is EditResult.Failure) }
    @Test fun a_trim_and_a_move_keep_the_key() {
        val keyed = ok(TimelineOperations.setPipChromaKey(withPip, "p", ChromaKey(0x0000FF)))
        assertEquals(0x0000FF, ok(TimelineOperations.movePip(ok(TimelineOperations.trimPip(keyed, "p", 0, 2_000)), "p", 3_000)).pipClips.single().chromaKey?.rgb)
    }
}
