package io.bsoft.bconverter.media.player

import io.bsoft.bconverter.fakes.FakePlayerEngine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {
    private val engine = FakePlayerEngine()
    private val controller = PlaybackController(engine)
    private val file = File("/tmp/a.mp4")
    private val state get() = controller.state.value
    private val events get() = engine.listener!!

    private fun ready(durationMs: Long = 10_000) { controller.load(file); events.onReady(durationMs) }
    private fun playing() { ready(); controller.play(); events.onIsPlayingChanged(true) }

    // ---- loading ----
    @Test fun starts_idle() = assertEquals(PlaybackState(), state)

    @Test fun the_controller_listens_to_its_engine() = assertSame(controller, engine.listener)

    @Test fun loading_prepares_the_engine_and_shows_loading() {
        controller.load(file)
        assertEquals(PlaybackStatus.Loading, state.status); assertEquals(listOf(file), engine.prepared)
    }

    @Test fun loading_again_starts_from_scratch() {
        playing(); events.onPosition(5000)
        controller.load(File("/tmp/b.mp4"))
        assertEquals(PlaybackState(status = PlaybackStatus.Loading), state)
    }

    @Test fun the_engine_becoming_ready_publishes_the_duration() {
        ready(12_345)
        assertEquals(PlaybackState(status = PlaybackStatus.Ready, durationMs = 12_345), state)
    }

    @Test fun becoming_ready_again_after_a_seek_keeps_the_position() {
        ready(); controller.seekTo(4000); events.onReady(10_500)
        assertEquals(4000L, state.positionMs); assertEquals(10_500L, state.durationMs)
    }

    // ---- play / pause ----
    @Test fun play_is_ignored_until_ready() { controller.play(); controller.load(file); controller.play(); assertTrue(engine.calls.isEmpty()) }

    @Test fun play_asks_the_engine_and_playing_follows_its_report() {
        ready(); controller.play()
        assertEquals(listOf("play"), engine.calls); assertFalse(state.isPlaying)
        events.onIsPlayingChanged(true); assertTrue(state.isPlaying)
        events.onIsPlayingChanged(false); assertFalse(state.isPlaying)
    }

    @Test fun pause_only_acts_while_playing() {
        ready(); controller.pause(); assertTrue(engine.calls.isEmpty())
        playing(); controller.pause(); assertEquals(listOf("play", "pause"), engine.calls)
    }

    @Test fun toggle_switches_between_play_and_pause() {
        ready(); controller.togglePlayPause(); events.onIsPlayingChanged(true)
        controller.togglePlayPause()
        assertEquals(listOf("play", "pause"), engine.calls)
    }

    // ---- position ----
    @Test fun position_reports_are_published() { ready(); events.onPosition(2500); assertEquals(2500L, state.positionMs) }

    @Test fun position_is_kept_within_zero_and_the_duration() {
        ready(); events.onPosition(-40); assertEquals(0L, state.positionMs)
        events.onPosition(99_999); assertEquals(10_000L, state.positionMs)
    }

    @Test fun progress_is_position_over_duration() {
        ready(); events.onPosition(2500)
        assertEquals(0.25f, state.progress, 0.0001f)
        assertEquals(0f, PlaybackState().progress, 0f)
    }

    // ---- seeking ----
    @Test fun seeking_moves_the_engine_and_the_published_position() {
        ready(); controller.seekTo(4000)
        assertEquals(listOf("seek:4000"), engine.calls); assertEquals(4000L, state.positionMs)
    }

    @Test fun seeking_is_clamped_to_the_media() {
        ready(); controller.seekTo(-5); controller.seekTo(99_999)
        assertEquals(listOf("seek:0", "seek:10000"), engine.calls)
    }

    @Test fun seeking_is_ignored_when_nothing_can_be_played() {
        controller.seekTo(1000); controller.load(file); controller.seekTo(1000)
        events.onError(PlaybackError.Unsupported); controller.seekTo(1000)
        assertTrue(engine.calls.isEmpty())
    }

    // ---- end of media ----
    @Test fun reaching_the_end_stops_playback_at_the_duration() {
        playing(); events.onEnded()
        assertEquals(PlaybackStatus.Ended, state.status); assertFalse(state.isPlaying); assertEquals(10_000L, state.positionMs)
    }

    @Test fun playing_after_the_end_restarts_from_the_beginning() {
        playing(); events.onEnded(); engine.calls.clear()
        controller.play()
        assertEquals(listOf("seek:0", "play"), engine.calls)
        assertEquals(PlaybackStatus.Ready, state.status); assertEquals(0L, state.positionMs)
    }

    @Test fun seeking_back_from_the_end_makes_it_playable_again_but_seeking_to_the_end_does_not() {
        playing(); events.onEnded()
        controller.seekTo(10_000); assertEquals(PlaybackStatus.Ended, state.status)
        controller.seekTo(3000); assertEquals(PlaybackStatus.Ready, state.status); assertEquals(3000L, state.positionMs)
    }

    // ---- errors and release ----
    @Test fun an_error_stops_everything_and_is_reported() {
        playing(); events.onError(PlaybackError.Decoder)
        assertEquals(PlaybackStatus.Error, state.status); assertEquals(PlaybackError.Decoder, state.error); assertFalse(state.isPlaying)
        engine.calls.clear(); controller.play(); assertTrue(engine.calls.isEmpty())
    }

    @Test fun loading_after_an_error_clears_it() {
        controller.load(file); events.onError(PlaybackError.SourceNotFound); controller.load(file)
        assertNull(state.error); assertEquals(PlaybackStatus.Loading, state.status)
    }

    @Test fun releasing_frees_the_engine_and_resets_the_state() {
        playing(); controller.release()
        assertEquals("release", engine.calls.last()); assertEquals(PlaybackState(), state)
        assertNull(engine.listener)
    }

    @Test fun nothing_works_after_release() {
        ready(); controller.release(); engine.calls.clear()
        controller.play(); controller.seekTo(10); assertTrue(engine.calls.isEmpty())
    }
}
