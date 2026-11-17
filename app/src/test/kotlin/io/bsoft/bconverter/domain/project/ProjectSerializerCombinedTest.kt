package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Subtitles and text overlays live side by side in one project; saving and opening must keep both, in every combination. */
@RunWith(RobolectricTestRunner::class)
class ProjectSerializerCombinedTest {
    private val track = SubtitleTrack("s1", "English", listOf(SubtitleCue("c1", 0, 1000, "Hi")))
    private val text = TextOverlay("t1", "Title", 0, 2000, animIn = OverlayAnimation(AnimationKind.FADE))
    private fun stored(tracks: List<SubtitleTrack>, overlays: List<TextOverlay>) =
        StoredProject(VideoProject("p", "P", Timeline(emptyList(), emptyList()), TL.exportSettings(), subtitleTracks = tracks, textOverlays = overlays), 1, 2)
    private fun roundTrip(s: StoredProject) = (ProjectSerializer.decode(ProjectSerializer.encode(s)) as DecodeResult.Decoded).stored.project

    @Test fun both_together_round_trip() { val p = roundTrip(stored(listOf(track), listOf(text))); assertEquals(listOf(track), p.subtitleTracks); assertEquals(listOf(text), p.textOverlays) }
    @Test fun only_subtitles_keep_no_overlays() { val p = roundTrip(stored(listOf(track), emptyList())); assertEquals(listOf(track), p.subtitleTracks); assertTrue(p.textOverlays.isEmpty()) }
    @Test fun only_overlays_keep_no_subtitles() { val p = roundTrip(stored(emptyList(), listOf(text))); assertTrue(p.subtitleTracks.isEmpty()); assertEquals(listOf(text), p.textOverlays) }
    @Test fun encoding_twice_gives_the_same_text() { val s = stored(listOf(track), listOf(text)); assertEquals(ProjectSerializer.encode(s), ProjectSerializer.encode(StoredProject(roundTrip(s), 1, 2))) }
    @Test fun many_overlays_save_and_open_quickly() {
        val many = (1..500).map { TextOverlay("t$it", "Line $it", it * 10L, it * 10L + 3000, centerX = (it % 100) / 100f) }
        val start = System.nanoTime(); val p = roundTrip(stored(emptyList(), many)); val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(many, p.textOverlays); assertTrue("took $ms ms", ms < 3_000)
    }
}
