package io.bsoft.bconverter.domain.render

import android.net.Uri
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RenderPlannerTest {
    private fun plan(p: VideoProject) = (RenderPlanner.plan(p) as RenderPlanResult.Success).plan
    private fun error(p: VideoProject) = (RenderPlanner.plan(p) as RenderPlanResult.Failure).error
    private fun single() = TL.timeline(TL.video("a", 10_000))
    private fun video(res: Resolution? = null, fps: Double? = null) = VideoSettings(VideoCodec.H264, VideoQuality.Crf(23), res, fps)

    // ---- video ----
    @Test fun a_single_clip_becomes_one_input_and_one_segment() {
        val p = plan(TL.project(single()))
        assertEquals(listOf(RenderInput(File("/tmp/a"), 0, 10_000)), p.inputs)
        assertEquals(Canvas(1920, 1080, 30.0), p.canvas)
        assertEquals(listOf(VideoSegment(0, 1f)), p.videoSegments); assertEquals(10_000L, p.durationMs)
    }

    @Test fun several_clips_keep_their_order_and_add_up() {
        val p = plan(TL.project(TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000), TL.video("c", 5_000))))
        assertEquals(listOf("a", "b", "c"), p.inputs.map { it.file.name }) // names, not paths: separators differ on Windows
        assertEquals(listOf(0, 1, 2), p.videoSegments.map { it.inputIndex }); assertEquals(35_000L, p.durationMs)
    }

    @Test fun a_trimmed_clip_seeks_to_its_source_start_for_its_source_span() {
        val p = plan(TL.project(TL.timeline(TL.video("a", 6_000, sourceStart = 2_000, sourceDuration = 10_000))))
        assertEquals(RenderInput(File("/tmp/a"), 2_000, 6_000), p.inputs.single())
    }

    @Test fun speed_changes_the_timeline_duration_but_not_the_source_span() {
        val p = plan(TL.project(TL.timeline(TL.video("a", 10_000, speed = 2f))))
        assertEquals(10_000L, p.inputs.single().durationMs); assertEquals(2f, p.videoSegments.single().speed, 0f)
        assertEquals(5_000L, p.durationMs)
    }

    // ---- canvas ----
    @Test fun the_export_settings_decide_the_canvas() =
        assertEquals(Canvas(1280, 720, 24.0), plan(TL.project(single(), TL.exportSettings(video(Resolution(1280, 720), 24.0)))).canvas)

    @Test fun odd_canvas_sizes_are_rounded_down_to_even() =
        assertEquals(Canvas(1280, 720, 30.0), plan(TL.project(single(), TL.exportSettings(video(Resolution(1281, 721))))).canvas)

    @Test fun a_rotated_source_uses_its_display_size() =
        assertEquals(Canvas(1080, 1920, 30.0), plan(TL.project(single(), metadata = { TL.meta(rotation = 90) })).canvas)

    @Test fun an_unknown_frame_rate_defaults_to_30() = assertEquals(30.0, plan(TL.project(single(), metadata = { TL.meta(fps = null) })).canvas.frameRate, 0.0)

    @Test fun without_settings_the_first_clip_decides_the_canvas() {
        val t = TL.timeline(TL.video("a"), TL.video("b"))
        val p = plan(TL.project(t, metadata = { if (it == TL.source("a")) TL.meta(1280, 720, 25.0) else TL.meta() }))
        assertEquals(Canvas(1280, 720, 25.0), p.canvas)
    }

    // ---- audio clips ----
    @Test fun audio_clips_become_delayed_overlays_after_the_video_inputs() {
        val t = TL.withAudio(single(), listOf(TL.audio("m", 5_000, start = 2_000, volume = 0.5f, sourceStart = 1_000, sourceDuration = 8_000)))
        val p = plan(TL.project(t))
        assertEquals(listOf(OverlayAudio(1, 0.5f, 2_000)), p.overlays)
        assertEquals(RenderInput(File("/tmp/m"), 1_000, 5_000), p.inputs[1])
    }

    @Test fun audio_running_past_the_video_is_kept_and_the_duration_stays_the_video_duration() {
        val p = plan(TL.project(TL.withAudio(single(), listOf(TL.audio("m", 5_000, start = 8_000)))))
        assertEquals(1, p.overlays.size); assertEquals(10_000L, p.durationMs)
    }

    // ---- original audio ----
    private fun two() = TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000, speed = 2f))

    @Test fun original_audio_follows_each_clip_with_its_speed_and_timeline_duration() =
        assertEquals(listOf(OriginalAudioSegment(0, 1f, 10_000), OriginalAudioSegment(1, 2f, 10_000)), plan(TL.project(two())).originalAudio)

    @Test fun a_clip_without_audio_becomes_silence_of_the_same_length() {
        val p = plan(TL.project(two(), metadata = { if (it == TL.source("b")) TL.meta(audio = false) else TL.meta() }))
        assertEquals(listOf(OriginalAudioSegment(0, 1f, 10_000), OriginalAudioSegment(null, 2f, 10_000)), p.originalAudio)
    }

    @Test fun no_original_audio_when_no_clip_has_any() = assertNull(plan(TL.project(two(), metadata = { TL.meta(audio = false) })).originalAudio)

    @Test fun replacing_the_original_audio_keeps_only_the_added_audio() {
        val t = TL.withAudio(two(), listOf(TL.audio("m")))
        val p = plan(TL.project(t, TL.exportSettings(keepOriginalAudio = false)))
        assertNull(p.originalAudio); assertEquals(1, p.overlays.size)
    }

    @Test fun without_audio_settings_the_output_has_no_audio_at_all() {
        val p = plan(TL.project(TL.withAudio(two(), listOf(TL.audio("m"))), TL.exportSettings(audio = null)))
        assertNull(p.originalAudio); assertTrue(p.overlays.isEmpty()); assertNull(p.audioFormat)
    }

    @Test fun the_audio_working_format_comes_from_the_settings() {
        assertEquals(AudioFormatSpec(48_000, 2), plan(TL.project(single())).audioFormat)
        assertEquals(AudioFormatSpec(44_100, 1), plan(TL.project(single(), TL.exportSettings(audio = AudioSettings(AudioCodec.AAC, 128, 44_100, 1)))).audioFormat)
        assertEquals(AudioFormatSpec(48_000, 2), plan(TL.project(single(), TL.exportSettings(audio = AudioSettings(AudioCodec.AAC, 128, null, 6)))).audioFormat)
    }

    // ---- errors ----
    @Test fun an_empty_video_track_cannot_be_rendered() = assertEquals(RenderError.NoVideo, error(TL.project(Timeline())))

    @Test fun every_video_source_needs_probe_information() =
        assertEquals(RenderError.MissingSourceInfo(TL.source("a")), error(TL.project(single(), metadata = { null })))

    @Test fun sources_must_be_staged_as_files() {
        val uri = MediaSource.UriSource(Uri.parse("content://x/1"))
        val clip = VideoClip("a", uri, 10_000, 0, 10_000)
        assertEquals(RenderError.SourceNotStaged(uri), error(TL.project(Timeline(videoClips = listOf(clip)).normalized())))
    }
}
