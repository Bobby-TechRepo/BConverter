package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 11 (spec §26, §59): the timeline is the source of truth; preview only interprets it. */
class PreviewMapperTest {
    // a: 0..10_000 (1x), b: source 5_000..15_000 played at 2x => 5_000 ms long, sits at 10_000..15_000
    private val timeline = TL.timeline(
        TL.video("a", spanMs = 10_000),
        TL.video("b", spanMs = 10_000, speed = 2f, sourceStart = 5_000),
    )

    @Test fun position_inside_first_clip_maps_to_its_source_time() {
        val p = PreviewMapper.positionAt(timeline, 4_000)!!
        assertEquals("a", p.clip.id)
        assertEquals(0, p.clipIndex)
        assertEquals(4_000, p.sourcePositionMs)
    }

    @Test fun position_in_a_sped_up_clip_advances_the_source_faster() {
        val p = PreviewMapper.positionAt(timeline, 11_000)!! // 1_000 ms into b at 2x = 2_000 ms of source
        assertEquals("b", p.clip.id)
        assertEquals(1, p.clipIndex)
        assertEquals(5_000 + 2_000, p.sourcePositionMs)
    }

    @Test fun a_clip_boundary_belongs_to_the_later_clip() {
        val p = PreviewMapper.positionAt(timeline, 10_000)!!
        assertEquals("b", p.clip.id)
        assertEquals(5_000, p.sourcePositionMs)
    }

    @Test fun time_zero_maps_to_the_start_of_the_first_clip() {
        assertEquals(0, PreviewMapper.positionAt(timeline, 0)!!.sourcePositionMs)
    }

    @Test fun time_at_or_after_the_end_of_the_video_has_no_position() {
        assertNull(PreviewMapper.positionAt(timeline, timeline.videoDurationMs))
        assertNull(PreviewMapper.positionAt(timeline, timeline.videoDurationMs + 1))
    }

    @Test fun negative_time_has_no_position() { assertNull(PreviewMapper.positionAt(timeline, -1)) }

    @Test fun empty_timeline_has_no_position() { assertNull(PreviewMapper.positionAt(Timeline(), 0)) }

    @Test fun source_time_maps_back_to_timeline_time() {
        val b = timeline.videoClips[1]
        assertEquals(10_000, PreviewMapper.timelineTimeOf(b, 5_000))
        assertEquals(11_000, PreviewMapper.timelineTimeOf(b, 7_000))
    }

    @Test fun source_time_outside_the_clip_range_is_clamped_to_the_clip() {
        val b = timeline.videoClips[1]
        assertEquals(10_000, PreviewMapper.timelineTimeOf(b, 0))
        assertEquals(b.endTimeMs, PreviewMapper.timelineTimeOf(b, 99_000))
    }

    @Test fun mapping_forward_and_back_round_trips() {
        for (t in listOf(0L, 1_234L, 9_999L, 10_000L, 12_500L, 14_999L)) {
            val p = PreviewMapper.positionAt(timeline, t)!!
            assertEquals(t, PreviewMapper.timelineTimeOf(p.clip, p.sourcePositionMs))
        }
    }

    // ---- segments (what a player needs: ordered, clipped, with speed) ----
    @Test fun segments_follow_the_video_track_in_order() {
        val s = PreviewMapper.segments(timeline)
        assertEquals(listOf("a", "b"), s.map { it.clipId })
        assertEquals(listOf(0L, 10_000L), s.map { it.timelineStartMs })
        assertEquals(listOf(1f, 2f), s.map { it.speed })
        assertEquals(5_000L, s[1].sourceStartMs)
        assertEquals(15_000L, s[1].sourceEndMs)
    }

    @Test fun segments_of_an_empty_timeline_are_empty() { assertTrue(PreviewMapper.segments(Timeline()).isEmpty()) }

    // ---- audio ----
    @Test fun active_audio_lists_clips_covering_the_time_with_their_source_position() {
        val t = TL.withAudio(
            timeline,
            listOf(TL.audio("m", spanMs = 8_000, start = 2_000, sourceStart = 1_000)),
            listOf(TL.audio("v", spanMs = 3_000, start = 6_000)),
        )
        val at7 = PreviewMapper.activeAudioAt(t, 7_000)
        assertEquals(listOf("m", "v"), at7.map { it.clip.id }.sorted())
        assertEquals(1_000 + 5_000, at7.first { it.clip.id == "m" }.sourcePositionMs)
        assertEquals(1_000, at7.first { it.clip.id == "v" }.sourcePositionMs)
    }

    @Test fun audio_is_active_from_its_start_up_to_but_excluding_its_end() {
        val t = TL.withAudio(timeline, listOf(TL.audio("m", spanMs = 1_000, start = 2_000)))
        assertTrue(PreviewMapper.activeAudioAt(t, 1_999).isEmpty())
        assertEquals(1, PreviewMapper.activeAudioAt(t, 2_000).size)
        assertEquals(1, PreviewMapper.activeAudioAt(t, 2_999).size)
        assertTrue(PreviewMapper.activeAudioAt(t, 3_000).isEmpty())
    }

    @Test fun audio_can_play_after_the_video_has_ended() {
        val t = TL.withAudio(timeline, listOf(TL.audio("tail", spanMs = 5_000, start = 14_000)))
        assertNull(PreviewMapper.positionAt(t, 16_000))
        assertEquals(1, PreviewMapper.activeAudioAt(t, 16_000).size)
    }

    @Test fun segments_tell_the_player_which_clips_are_muted() {
        val t = TL.timeline(TL.video("a", 5_000), TL.video("b", 5_000, muted = true))
        assertEquals(listOf(false, true), PreviewMapper.segments(t).map { it.muted })
    }
}
