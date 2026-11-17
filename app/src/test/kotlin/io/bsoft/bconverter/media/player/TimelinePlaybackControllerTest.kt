package io.bsoft.bconverter.media.player

import android.net.Uri
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.SeekRange
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.fakes.FakeTimelinePlayerEngine
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Phase 11 (spec §26/§59): timeline -> playback state -> player. The player is a fake; the timeline stays the source of truth. */
@RunWith(RobolectricTestRunner::class)
class TimelinePlaybackControllerTest {
    private val engine = FakeTimelinePlayerEngine()
    private val controller = TimelinePlaybackController(engine)
    private val state get() = controller.state.value

    // a: 0..10_000; b: source 5_000..15_000 at 2x => 10_000..15_000
    private val timeline = TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000, speed = 2f, sourceStart = 5_000))

    private fun ready(t: Timeline = timeline) { controller.setTimeline(t); engine.listener!!.onReady(); engine.clear() }
    private fun playing(t: Timeline = timeline) { ready(t); controller.play(); engine.listener!!.onIsPlayingChanged(true); engine.clear() }

    // ---- loading ----
    @Test fun an_empty_timeline_has_nothing_to_play() {
        controller.setTimeline(Timeline())
        assertEquals(PlaybackStatus.Idle, state.status); assertEquals(0L, state.durationMs); assertFalse(engine.calls.any { it.startsWith("prepare") })
    }

    @Test fun a_timeline_is_prepared_as_ordered_segments_and_its_duration_is_the_video_length() {
        controller.setTimeline(timeline)
        assertEquals(PlaybackStatus.Loading, state.status); assertEquals(15_000L, state.durationMs)
        assertEquals(listOf("prepare:a,b"), engine.calls.filter { it.startsWith("prepare") })
    }

    @Test fun the_player_reporting_ready_makes_the_state_ready() {
        controller.setTimeline(timeline); engine.listener!!.onReady()
        assertEquals(PlaybackStatus.Ready, state.status); assertEquals(0L, state.positionMs)
    }

    @Test fun a_video_that_is_not_a_prepared_file_cannot_be_previewed() {
        val unstaged = Timeline(listOf(VideoClip("u", MediaSource.UriSource(Uri.parse("content://x/1")), 5_000, 0, 5_000))).normalized()
        controller.setTimeline(unstaged)
        assertEquals(PlaybackStatus.Error, state.status); assertEquals(PlaybackError.SourceNotFound, state.error)
        assertFalse(engine.calls.any { it.startsWith("prepare") })
    }

    // ---- play / pause ----
    @Test fun play_is_ignored_until_the_player_is_ready() {
        controller.setTimeline(timeline); controller.play()
        assertFalse(engine.calls.contains("play"))
    }

    @Test fun play_and_pause_drive_the_player_and_follow_its_reports() {
        ready()
        controller.play(); assertEquals(listOf("play"), engine.calls)
        engine.listener!!.onIsPlayingChanged(true); assertTrue(state.isPlaying)
        controller.pause(); assertTrue(engine.calls.contains("pause"))
        engine.listener!!.onIsPlayingChanged(false); assertFalse(state.isPlaying)
    }

    @Test fun toggle_switches_between_play_and_pause() {
        playing()
        controller.togglePlayPause(); assertTrue(engine.calls.contains("pause"))
    }

    // ---- seeking ----
    @Test fun seeking_maps_the_timeline_time_to_the_right_clip_and_source_time() {
        ready(); controller.seekTo(11_000)
        assertEquals(listOf("seek:1@7000"), engine.calls); assertEquals(11_000L, state.positionMs)
    }

    @Test fun seeking_before_the_start_goes_to_the_start() {
        ready(); controller.seekTo(-500)
        assertEquals(listOf("seek:0@0"), engine.calls); assertEquals(0L, state.positionMs)
    }

    @Test fun seeking_past_the_end_stops_at_the_end_of_the_blank_area_with_the_player_on_its_last_instant() {
        ready(); controller.seekTo(99_000)
        assertEquals(15_000L + SeekRange.BLANK_TAIL_MS, state.positionMs)
        assertEquals(1, engine.calls.single().substringAfter("seek:").substringBefore('@').toInt())
    }

    @Test fun seeking_into_the_blank_area_is_allowed_and_reported_as_blank() {
        ready(); controller.seekTo(18_000)
        assertEquals(18_000L, state.positionMs); assertTrue(state.isBlank)
        controller.seekTo(15_000); assertFalse(state.isBlank)
        controller.seekTo(14_999); assertFalse(state.isBlank)
    }

    @Test fun playing_from_the_blank_area_starts_again_from_the_beginning() {
        ready(); controller.seekTo(18_000); engine.clear()
        controller.play()
        assertEquals(listOf("seek:0@0", "play"), engine.calls)
        assertEquals(0L, state.positionMs); assertFalse(state.isBlank)
    }

    @Test fun seeking_into_the_blank_area_while_playing_pauses_the_player() {
        playing(); controller.seekTo(18_000)
        assertEquals(listOf("pause", "seek:1@14998"), engine.calls.filter { it == "pause" || it.startsWith("seek") })
        assertTrue(state.isBlank)
    }

    @Test fun seeking_out_of_the_blank_area_after_the_end_makes_it_playable_again() {
        playing(); engine.listener!!.onEnded()
        controller.seekTo(18_000); assertEquals(PlaybackStatus.Ended, state.status); assertTrue(state.isBlank)
        controller.seekTo(3_000); assertEquals(PlaybackStatus.Ready, state.status)
    }

    @Test fun seeking_is_ignored_while_loading() {
        controller.setTimeline(timeline); controller.seekTo(3_000)
        assertFalse(engine.calls.any { it.startsWith("seek") }); assertEquals(0L, state.positionMs)
    }

    @Test fun the_player_reporting_progress_moves_the_timeline_position_across_clips() {
        ready()
        engine.listener!!.onVideoPosition(0, 4_000); assertEquals(4_000L, state.positionMs)
        engine.listener!!.onVideoPosition(1, 7_000); assertEquals(11_000L, state.positionMs)
    }

    @Test fun a_progress_report_for_an_unknown_segment_is_ignored() {
        ready(); engine.listener!!.onVideoPosition(7, 1_000)
        assertEquals(0L, state.positionMs)
    }

    // ---- end ----
    @Test fun reaching_the_end_stops_playback_at_the_duration() {
        playing(); engine.listener!!.onEnded()
        assertEquals(PlaybackStatus.Ended, state.status); assertFalse(state.isPlaying); assertEquals(15_000L, state.positionMs)
    }

    @Test fun playing_after_the_end_restarts_from_the_beginning() {
        playing(); engine.listener!!.onEnded(); engine.clear()
        controller.play()
        assertEquals(listOf("seek:0@0", "play"), engine.calls.filter { it.startsWith("seek") || it == "play" })
        assertEquals(PlaybackStatus.Ready, state.status); assertEquals(0L, state.positionMs)
    }

    // ---- errors ----
    @Test fun a_player_error_is_reported_and_blocks_playback() {
        ready(); engine.listener!!.onError(PlaybackError.Decoder); engine.clear()
        assertEquals(PlaybackStatus.Error, state.status); assertEquals(PlaybackError.Decoder, state.error)
        controller.play(); assertFalse(engine.calls.contains("play"))
    }

    // ---- edits while previewing ----
    @Test fun an_edited_timeline_is_prepared_again_and_the_position_is_restored() {
        ready(); controller.seekTo(4_000)
        val edited = TL.timeline(TL.video("a", 10_000), TL.video("c", 2_000))
        engine.clear(); controller.setTimeline(edited)
        assertEquals(PlaybackStatus.Loading, state.status); assertEquals(12_000L, state.durationMs)
        engine.clear(); engine.listener!!.onReady()
        assertEquals(listOf("seek:0@4000"), engine.calls); assertEquals(4_000L, state.positionMs)
    }

    @Test fun the_restored_position_is_kept_within_a_shorter_timeline() {
        ready(); controller.seekTo(24_000)
        controller.setTimeline(TL.timeline(TL.video("a", 5_000))); engine.listener!!.onReady()
        assertEquals(5_000L + SeekRange.BLANK_TAIL_MS, state.positionMs)
    }

    @Test fun editing_while_playing_pauses_and_silences_audio() {
        playing(); controller.setTimeline(TL.timeline(TL.video("a", 10_000)))
        assertFalse(state.isPlaying); assertTrue(engine.calls.contains("stopAllAudio"))
    }

    @Test fun removing_every_clip_returns_to_idle() {
        ready(); controller.setTimeline(Timeline())
        assertEquals(PlaybackStatus.Idle, state.status); assertEquals(0L, state.positionMs)
    }

    // ---- audio ----
    private val withMusic = TL.withAudio(
        TL.timeline(TL.video("v", 20_000)),
        listOf(TL.audio("m", spanMs = 4_000, start = 5_000, sourceStart = 1_000, volume = 0.5f)),
    )

    @Test fun audio_starts_when_the_playhead_enters_its_clip_with_the_right_source_time_and_volume() {
        playing(withMusic)
        engine.listener!!.onVideoPosition(0, 4_000); assertTrue(engine.calls.none { it.startsWith("startAudio") })
        engine.listener!!.onVideoPosition(0, 6_000); assertEquals(listOf("startAudio:m@2000x0.5"), engine.calls)
    }

    @Test fun a_running_audio_clip_is_not_restarted_by_later_progress_reports() {
        playing(withMusic)
        engine.listener!!.onVideoPosition(0, 6_000); engine.listener!!.onVideoPosition(0, 6_050); engine.listener!!.onVideoPosition(0, 6_100)
        assertEquals(1, engine.calls.count { it.startsWith("startAudio") })
    }

    @Test fun audio_stops_when_the_playhead_leaves_its_clip() {
        playing(withMusic)
        engine.listener!!.onVideoPosition(0, 6_000); engine.clear()
        engine.listener!!.onVideoPosition(0, 9_000)
        assertEquals(listOf("stopAudio:m"), engine.calls)
    }

    @Test fun pausing_silences_audio() {
        playing(withMusic); engine.listener!!.onVideoPosition(0, 6_000); engine.clear()
        controller.pause(); assertTrue(engine.calls.contains("stopAllAudio"))
    }

    @Test fun no_audio_starts_while_paused() {
        ready(withMusic); engine.listener!!.onVideoPosition(0, 6_000)
        assertTrue(engine.calls.none { it.startsWith("startAudio") })
    }

    @Test fun seeking_while_playing_restarts_the_audio_that_should_sound_at_the_new_time() {
        playing(withMusic); engine.listener!!.onVideoPosition(0, 6_000); engine.clear()
        controller.seekTo(7_000)
        assertEquals(listOf("seek:0@7000", "stopAllAudio", "startAudio:m@3000x0.5"), engine.calls)
    }

    @Test fun seeking_while_paused_does_not_start_audio() {
        ready(withMusic); controller.seekTo(7_000)
        assertTrue(engine.calls.none { it.startsWith("startAudio") })
    }

    @Test fun playing_from_the_middle_of_an_audio_clip_starts_it_at_the_right_point() {
        ready(withMusic); controller.seekTo(7_000); engine.clear()
        controller.play(); assertEquals(listOf("play", "startAudio:m@3000x0.5"), engine.calls)
    }

    @Test fun reaching_the_end_silences_audio() {
        playing(withMusic); engine.clear(); engine.listener!!.onEnded()
        assertTrue(engine.calls.contains("stopAllAudio"))
    }

    @Test fun audio_that_is_not_a_prepared_file_is_skipped_but_the_video_still_plays() {
        val unstagedAudio = TL.withAudio(TL.timeline(TL.video("v", 20_000)),
            listOf(io.bsoft.bconverter.domain.timeline.AudioClip("u", MediaSource.UriSource(Uri.parse("content://x/9")), 5_000, 0, 5_000, 1f, 0)))
        playing(unstagedAudio); engine.listener!!.onVideoPosition(0, 1_000)
        assertEquals(PlaybackStatus.Ready, state.status); assertTrue(engine.calls.none { it.startsWith("startAudio") })
    }

    // ---- release ----
    @Test fun releasing_frees_the_player_and_resets_the_state() {
        playing(); controller.release()
        assertTrue(engine.released); assertNull(engine.listener); assertEquals(PlaybackStatus.Idle, state.status); assertEquals(0L, state.positionMs)
    }
}
