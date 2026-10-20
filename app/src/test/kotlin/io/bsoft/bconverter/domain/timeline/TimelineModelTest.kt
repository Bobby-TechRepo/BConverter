package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineModelTest {
    // ---- video clips ----
    @Test fun video_duration_follows_the_source_span_and_speed() {
        assertEquals(10_000L, TL.video("a", 10_000).durationMs)
        assertEquals(5_000L, TL.video("a", 10_000, speed = 2f).durationMs)
        assertEquals(20_000L, TL.video("a", 10_000, speed = 0.5f).durationMs)
        assertEquals(40_000L, TL.video("a", 10_000, speed = 0.25f).durationMs)
    }

    @Test fun a_clip_ends_after_its_duration() = assertEquals(14_000L, TL.video("a", 10_000, start = 4_000).endTimeMs)

    private fun video(start: Long = 0, end: Long = 5_000, sourceDuration: Long = 10_000, speed: Float = 1f, at: Long = 0) =
        VideoClip("a", TL.source(), sourceDuration, start, end, speed, at)

    @Test(expected = IllegalArgumentException::class) fun source_start_cannot_be_negative() { video(start = -1) }
    @Test(expected = IllegalArgumentException::class) fun a_zero_length_range_is_rejected() { video(start = 5_000, end = 5_000) }
    @Test(expected = IllegalArgumentException::class) fun the_range_cannot_run_backwards() { video(start = 6_000, end = 5_000) }
    @Test(expected = IllegalArgumentException::class) fun the_range_cannot_exceed_the_source() { video(end = 10_001) }
    @Test(expected = IllegalArgumentException::class) fun speed_cannot_be_zero() { video(speed = 0f) }
    @Test(expected = IllegalArgumentException::class) fun speed_cannot_exceed_four() { video(speed = 4.5f) }
    @Test(expected = IllegalArgumentException::class) fun speed_cannot_be_below_a_quarter() { video(speed = 0.1f) }
    @Test(expected = IllegalArgumentException::class) fun speed_cannot_be_nan() { video(speed = Float.NaN) }
    @Test(expected = IllegalArgumentException::class) fun the_timeline_start_cannot_be_negative() { video(at = -1) }

    // ---- audio clips ----
    private fun audio(volume: Float = 1f, end: Long = 5_000, at: Long = 0) =
        AudioClip("m", TL.source(), 10_000, 0, end, volume, at)

    @Test fun audio_duration_is_the_source_span() = assertEquals(5_000L, TL.audio("m", 5_000).durationMs)
    @Test fun volume_between_zero_and_two_is_allowed() { audio(0f); audio(1f); audio(2f) }
    @Test(expected = IllegalArgumentException::class) fun negative_volume_is_rejected() { audio(volume = -0.1f) }
    @Test(expected = IllegalArgumentException::class) fun volume_above_two_is_rejected() { audio(volume = 2.1f) }
    @Test(expected = IllegalArgumentException::class) fun audio_range_beyond_the_source_is_rejected() { audio(end = 10_001) }
    @Test(expected = IllegalArgumentException::class) fun audio_cannot_start_before_zero() { audio(at = -5) }

    // ---- timeline ----
    @Test fun an_empty_timeline_has_no_duration() {
        assertEquals(0L, Timeline().durationMs); assertEquals(0L, Timeline().videoDurationMs)
    }

    @Test fun video_clips_sit_back_to_back_in_order() {
        val t = TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000))
        assertEquals(listOf(0L, 10_000L), t.videoClips.map { it.startTimeMs })
        assertEquals(30_000L, t.videoDurationMs); assertEquals(30_000L, t.durationMs)
    }

    @Test fun normalising_repairs_arbitrary_start_times() {
        val t = Timeline(videoClips = listOf(TL.video("a", 10_000, start = 99), TL.video("b", 5_000, start = 5))).normalized()
        assertEquals(listOf(0L, 10_000L), t.videoClips.map { it.startTimeMs })
    }

    @Test fun audio_can_extend_the_timeline_beyond_the_video() {
        val t = TL.withAudio(TL.timeline(TL.video("a", 10_000)), listOf(TL.audio("m", 5_000, start = 8_000)))
        assertEquals(13_000L, t.durationMs); assertEquals(10_000L, t.videoDurationMs)
    }

    @Test fun the_video_clip_under_a_time_is_found() {
        val t = TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000))
        assertEquals("a", t.videoClipAt(0)!!.id); assertEquals("a", t.videoClipAt(9_999)!!.id)
        assertEquals("b", t.videoClipAt(10_000)!!.id)
        assertNull(t.videoClipAt(30_000)); assertNull(t.videoClipAt(-1))
    }

    @Test fun any_clip_can_be_looked_up_by_id() {
        val t = TL.withAudio(TL.timeline(TL.video("a")), listOf(TL.audio("m")))
        assertEquals("a", t.findClip("a")!!.id); assertEquals("m", t.findClip("m")!!.id)
        assertNull(t.findClip("x")); assertTrue(t.contains("m")); assertFalse(t.contains("x"))
    }

    @Test fun audio_clips_are_listed_across_tracks_in_start_order() {
        val t = TL.withAudio(Timeline(), listOf(TL.audio("late", start = 9_000)), listOf(TL.audio("early", start = 1_000)))
        assertEquals(listOf("early", "late"), t.audioClips.map { it.id }); assertEquals(2, t.clipCount)
    }

    // ---- source mapping ----
    @Test fun every_clip_source_can_be_replaced_without_touching_anything_else() {
        val t = TL.withAudio(TL.timeline(TL.video("a", 10_000, speed = 2f), TL.video("b")), listOf(TL.audio("m", 3_000, start = 500)))
        val mapped = t.mapSources { TL.source("moved-" + (it as io.bsoft.bconverter.domain.model.MediaSource.FileSource).file.name) }
        assertEquals(listOf(TL.source("moved-a"), TL.source("moved-b")), mapped.videoClips.map { it.source })
        assertEquals(TL.source("moved-m"), mapped.audioClips.single().source)
        assertEquals(t.videoClips.map { it.copy(source = TL.source("x")) }, mapped.videoClips.map { it.copy(source = TL.source("x")) })
        assertEquals(500L, mapped.audioClips.single().startTimeMs)
    }
}
