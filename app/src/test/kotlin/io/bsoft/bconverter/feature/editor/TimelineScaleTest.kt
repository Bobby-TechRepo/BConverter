package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.timeline.Transition
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure timeline geometry (spec §21: horizontal scrolling, zoom, playhead, clip selection). */
class TimelineScaleTest {
    private val scale = TimelineScale(pxPerSecond = 100f)

    // ---- time <-> x ----
    @Test fun time_maps_to_x_by_the_scale() {
        assertEquals(0f, scale.xOf(0), 0f); assertEquals(250f, scale.xOf(2_500), 0.001f)
    }
    @Test fun x_maps_back_to_time_rounded_to_the_nearest_millisecond() {
        assertEquals(2_500, scale.msAt(250f)); assertEquals(1_234, scale.msAt(123.4f))
    }
    @Test fun a_position_left_of_the_start_maps_to_time_zero() { assertEquals(0, scale.msAt(-40f)) }
    @Test fun the_width_of_a_span_follows_the_scale() { assertEquals(1_000f, scale.widthOf(10_000), 0.001f) }

    // ---- zoom ----
    @Test fun zooming_in_multiplies_the_scale() { assertEquals(200f, scale.zoomedBy(2f).pxPerSecond, 0.001f) }
    @Test fun zooming_is_limited_at_both_ends() {
        assertEquals(TimelineScale.MAX_PX_PER_SECOND, scale.zoomedBy(1_000f).pxPerSecond, 0f)
        assertEquals(TimelineScale.MIN_PX_PER_SECOND, scale.zoomedBy(0.0001f).pxPerSecond, 0f)
    }
    @Test fun a_nonsense_zoom_factor_changes_nothing() {
        assertEquals(100f, scale.zoomedBy(0f).pxPerSecond, 0f); assertEquals(100f, scale.zoomedBy(Float.NaN).pxPerSecond, 0f)
        assertEquals(100f, scale.zoomedBy(-2f).pxPerSecond, 0f)
    }
    @Test fun the_constructor_rejects_a_scale_outside_the_limits() {
        try { TimelineScale(0f); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) {}
        try { TimelineScale(Float.NaN); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) {}
    }

    // ---- fitting ----
    @Test fun fit_shows_the_whole_timeline_in_the_viewport() {
        val fit = TimelineScale.fitting(durationMs = 20_000, viewportPx = 400f)
        assertEquals(20f, fit.pxPerSecond, 0.001f)
    }
    @Test fun fit_of_an_empty_timeline_is_the_default_scale() {
        assertEquals(TimelineScale.DEFAULT_PX_PER_SECOND, TimelineScale.fitting(0, 400f).pxPerSecond, 0f)
    }
    @Test fun fit_is_clamped_to_the_zoom_limits() {
        assertEquals(TimelineScale.MIN_PX_PER_SECOND, TimelineScale.fitting(10_000_000, 400f).pxPerSecond, 0f)
        assertEquals(TimelineScale.MAX_PX_PER_SECOND, TimelineScale.fitting(100, 400f).pxPerSecond, 0f)
    }

    // ---- ruler ----
    @Test fun ruler_labels_are_never_closer_than_the_minimum_spacing() {
        for (pps in listOf(1f, 5f, 20f, 100f, 400f)) {
            val s = TimelineScale(pps)
            assertTrue("pps=$pps", s.rulerStepMs() / 1000f * pps >= TimelineScale.MIN_LABEL_SPACING_PX)
        }
    }
    @Test fun ruler_steps_are_round_numbers_of_seconds() {
        val round = setOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1_800, 3_600).map { it * 1000L }.toSet()
        for (pps in listOf(1f, 3f, 7f, 20f, 100f, 400f)) assertTrue(TimelineScale(pps).rulerStepMs() in round)
    }
    @Test fun zooming_out_never_makes_the_ruler_finer() {
        assertTrue(TimelineScale(10f).rulerStepMs() >= TimelineScale(100f).rulerStepMs())
    }
    @Test fun ticks_start_at_zero_and_step_up_to_the_end() {
        val s = TimelineScale(100f) // 64px min spacing at 100px/s -> 1s
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), s.ticks(3_000))
    }
    @Test fun ticks_of_an_empty_timeline_are_just_the_origin() { assertEquals(listOf(0L), scale.ticks(0)) }

    // ---- clip blocks ----
    @Test fun video_blocks_sit_back_to_back_with_widths_from_durations() {
        val t = TL.timeline(TL.video("a", 2_000), TL.video("b", 3_000, speed = 2f)) // b lasts 1_500
        val blocks = scale.videoBlocks(t)
        assertEquals(listOf("a", "b"), blocks.map { it.id })
        assertEquals(listOf(0f, 200f), blocks.map { it.xPx })
        assertEquals(listOf(200f, 150f), blocks.map { it.widthPx })
    }
    @Test fun a_tiny_clip_still_gets_a_block_wide_enough_to_tap() {
        val t = TL.timeline(TL.video("a", 10))
        assertEquals(TimelineScale.MIN_BLOCK_WIDTH_PX, scale.videoBlocks(t).single().widthPx, 0f)
    }
    @Test fun audio_blocks_are_grouped_by_track_at_their_start_times() {
        val t = TL.withAudio(TL.timeline(TL.video("v", 10_000)),
            listOf(TL.audio("m1", 2_000, start = 1_000), TL.audio("m2", 1_000, start = 5_000)),
            listOf(TL.audio("m3", 4_000, start = 0)))
        val lanes = scale.audioLanes(t)
        assertEquals(listOf(listOf("m1", "m2"), listOf("m3")), lanes.map { lane -> lane.map { it.id } })
        assertEquals(listOf(100f, 500f), lanes[0].map { it.xPx }); assertEquals(400f, lanes[1][0].widthPx, 0.001f)
    }
    @Test fun the_content_width_covers_the_longest_lane_so_it_can_scroll_to_the_end() {
        val t = TL.withAudio(TL.timeline(TL.video("v", 5_000)), listOf(TL.audio("m", 3_000, start = 4_000)))
        assertEquals(scale.widthOf(7_000), scale.contentWidthPx(t), 0.001f)
    }
    @Test fun the_clip_under_a_touch_point_is_found_for_selection() {
        val t = TL.timeline(TL.video("a", 2_000), TL.video("b", 3_000))
        val blocks = scale.videoBlocks(t)
        assertEquals("a", blocks.hit(50f)?.id); assertEquals("b", blocks.hit(200f)?.id); assertEquals(null, blocks.hit(600f))
    }

    // ---- markers on the cuts between video clips ----
    private fun joined(vararg t: Pair<TransitionType, Long>): io.bsoft.bconverter.domain.timeline.Timeline {
        val clips = listOf(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000)).mapIndexed { i, c ->
            if (i == 0 || t.size < i) c else c.copy(transitionIn = Transition(t[i - 1].first, t[i - 1].second))
        }
        return io.bsoft.bconverter.domain.timeline.Timeline(clips).normalized()
    }

    @Test fun every_cut_gets_a_marker_and_the_first_clip_has_none() {
        val m = scale.joinMarkers(joined())
        assertEquals(listOf("b", "c"), m.map { it.clipId })
        assertEquals(listOf(1_000f, 2_000f), m.map { it.centerPx })
        assertTrue(m.all { it.widthPx == TimelineScale.MIN_MARKER_WIDTH_PX && !it.effect.isActive })
    }

    @Test fun a_fade_through_black_marker_is_centred_on_the_cut_and_as_wide_as_the_fade() {
        val m = scale.joinMarkers(joined(TransitionType.FADE_BLACK to 2_000L)).first()
        assertEquals(1_000f, m.centerPx, 0.001f); assertEquals(200f, m.widthPx, 0.001f)
    }

    @Test fun a_crossfade_marker_covers_the_overlap() {
        val m = scale.joinMarkers(joined(TransitionType.CROSSFADE to 2_000L)).first()
        // b now starts at 8 s (x = 800); the overlap runs 800..1000.
        assertEquals(900f, m.centerPx, 0.001f); assertEquals(200f, m.widthPx, 0.001f)
    }

    @Test fun a_short_transition_keeps_a_marker_big_enough_to_tap() {
        assertEquals(TimelineScale.MIN_MARKER_WIDTH_PX, scale.joinMarkers(joined(TransitionType.FADE_BLACK to 250L)).first().widthPx, 0f)
    }

    @Test fun keyframes_sit_inside_their_block() {
        val scale = TimelineScale(100f)
        val block = ClipBlock("c", 50f, 400f)
        assertEquals(listOf(0f, 100f, 400f), scale.keyframeXs(block, listOf(0, 1_000, 4_000), 4_000))
    }
    @Test fun keyframes_outside_the_clip_are_pulled_to_its_ends_and_duplicates_dropped() {
        val scale = TimelineScale(100f)
        val block = ClipBlock("c", 0f, 200f)
        assertEquals(listOf(0f, 200f), scale.keyframeXs(block, listOf(-5, 9_000, 9_500), 2_000))
    }
    @Test fun a_block_that_is_wider_than_its_clip_still_keeps_keyframes_inside() {
        val scale = TimelineScale(10f)
        val tiny = ClipBlock("c", 0f, 24f) // the minimum width is larger than the real clip
        assertEquals(listOf(0f, 5f), scale.keyframeXs(tiny, listOf(0, 500), 500))
    }
    @Test fun no_keyframes_or_a_zero_length_clip_gives_no_crash() {
        val scale = TimelineScale(100f)
        assertEquals(emptyList<Float>(), scale.keyframeXs(ClipBlock("c", 0f, 10f), emptyList(), 1_000))
        assertEquals(listOf(0f), scale.keyframeXs(ClipBlock("c", 0f, 10f), listOf(300), 0))
    }
}
