package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.timeline.FreezeFrame
import io.bsoft.bconverter.domain.timeline.PreviewMapper
import io.bsoft.bconverter.domain.timeline.TimelineOperations
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FreezeFrameGraphTest {
    private val frozen = ok(TimelineOperations.insertFreeze(TL.timeline(TL.video("a", 10_000)), "a", 4_000, "f", "a2", 2_000))
    private val plan get() = (RenderPlanner.plan(TL.project(frozen)) as RenderPlanResult.Success).plan

    @Test fun the_freeze_is_a_segment_of_its_own_with_its_length() {
        val s = plan.videoSegments; assertEquals(listOf(false, true, false), s.map { it.freeze }); assertEquals(2_000L, s[1].durationMs)
    }
    @Test fun the_freeze_has_no_sound_but_keeps_its_place_in_the_sound_track() {
        val a = plan.originalAudio!!; assertEquals(3, a.size); assertEquals(listOf(true, false, true), a.map { it.inputIndex != null }); assertEquals(2_000L, a[1].timelineDurationMs)
    }
    @Test fun the_input_is_a_short_window_at_the_chosen_time() {
        val i = plan.inputs[1]; assertEquals(4_000L, i.seekMs); assertEquals(FreezeFrame.INPUT_WINDOW_MS, i.durationMs)
    }
    @Test fun the_chain_keeps_one_picture_and_repeats_it_to_the_exact_length() {
        val f = FilterGraphBuilder.build(plan).chains.first { it.outputLabels == listOf("v1") }.filters
        assertEquals(listOf("trim=end_frame=1", "setpts=PTS-STARTPTS"), f.take(2))
        assertTrue("$f", f.contains("tpad=stop_mode=clone:stop_duration=2") && f.contains("trim=duration=2"))
        assertTrue(f.none { it.startsWith("setpts=(PTS") })
    }
    @Test fun ordinary_clips_are_untouched() {
        val f = FilterGraphBuilder.build(plan).chains.first { it.outputLabels == listOf("v0") }.filters
        assertTrue(f.none { it.startsWith("tpad") || it.startsWith("trim=") })
    }
    @Test fun the_preview_plays_the_freeze_as_slow_motion_lasting_its_whole_length() {
        val s = PreviewMapper.segments(frozen)[1]
        assertTrue(s.muted); assertEquals(2_000.0, (s.sourceEndMs - s.sourceStartMs) / s.speed.toDouble(), 5.0)
    }
}
