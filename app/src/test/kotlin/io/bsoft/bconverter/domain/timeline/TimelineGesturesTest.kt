package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGesturesTest {
    private fun ok(r: EditResult) = (r as EditResult.Success).timeline
    private fun err(r: EditResult) = (r as EditResult.Failure).error

    // ---------------- edge trim ----------------
    // a: source 2..6 s of a 10 s file; b: 0..5 s at double speed (2.5 s on screen).
    private val base = TL.timeline(TL.video("a", 4_000, sourceStart = 2_000, sourceDuration = 10_000), TL.video("b", 5_000, speed = 2f, sourceDuration = 5_000))

    @Test fun dragging_the_right_edge_extends_into_the_unused_source() {
        val t = ok(EdgeTrim.apply(base, "a", TrimEdge.END, 1_000))
        assertEquals(7_000L, t.videoClips[0].sourceEndMs)
        assertEquals(5_000L, t.videoClips[1].startTimeMs) // the track closes up behind it
    }

    @Test fun dragging_the_left_edge_trims_the_start_and_the_track_closes_up() {
        val t = ok(EdgeTrim.apply(base, "a", TrimEdge.START, 1_500))
        assertEquals(3_500L, t.videoClips[0].sourceStartMs)
        assertEquals(0L, t.videoClips[0].startTimeMs)
        assertEquals(2_500L, t.videoClips[1].startTimeMs)
    }

    @Test fun a_drag_is_scaled_by_the_clip_speed() {
        val t = ok(EdgeTrim.apply(base, "b", TrimEdge.END, -1_000))
        assertEquals(3_000L, t.videoClips[1].sourceEndMs) // 1 s on screen = 2 s of source at 2x
    }

    @Test fun drags_stop_at_the_source_and_at_the_minimum_length() {
        assertEquals(-2_000L, EdgeTrim.clampDelta(base, "a", TrimEdge.START, -9_000)) // only 2 s of source before
        assertEquals(4_000L, EdgeTrim.clampDelta(base, "a", TrimEdge.END, 9_000))      // 4 s after
        assertEquals(-(4_000L - EdgeTrim.MIN_CLIP_MS), EdgeTrim.clampDelta(base, "a", TrimEdge.END, -9_000))
        assertEquals(0L, EdgeTrim.clampDelta(base, "b", TrimEdge.END, 1_000))         // already at the end of its source
    }

    @Test fun a_reversed_clip_trims_its_source_from_the_other_end() {
        val reversed = base.copy(videoClips = base.videoClips.mapIndexed { i, c -> if (i == 0) c.copy(reversed = true) else c })
        val t = ok(EdgeTrim.apply(reversed, "a", TrimEdge.START, 1_000))
        // The left edge of a reversed clip is the end of its source.
        assertEquals(5_000L, t.videoClips[0].sourceEndMs); assertEquals(2_000L, t.videoClips[0].sourceStartMs)
    }

    @Test fun the_left_edge_of_an_audio_clip_moves_its_start_too() {
        val t0 = TL.withAudio(base, listOf(TL.audio("m", 5_000, start = 1_000, sourceStart = 2_000, sourceDuration = 20_000)))
        val m = ok(EdgeTrim.apply(t0, "m", TrimEdge.START, -500)).audioClips.single()
        assertEquals(500L, m.startTimeMs); assertEquals(1_500L, m.sourceStartMs); assertEquals(5_500L, m.durationMs)
        // Not before time zero.
        assertEquals(-1_000L, EdgeTrim.clampDelta(t0, "m", TrimEdge.START, -9_000))
    }

    @Test fun an_audio_edge_stops_at_its_neighbour_on_the_lane() {
        val t0 = TL.withAudio(base, listOf(TL.audio("m1", 2_000, start = 0, sourceDuration = 20_000), TL.audio("m2", 2_000, start = 3_000, sourceStart = 5_000, sourceDuration = 20_000)))
        assertEquals(1_000L, EdgeTrim.clampDelta(t0, "m1", TrimEdge.END, 5_000))
        assertEquals(-1_000L, EdgeTrim.clampDelta(t0, "m2", TrimEdge.START, -5_000))
    }

    @Test fun freeze_frames_and_unknown_clips_cannot_be_dragged() {
        val frozen = base.copy(videoClips = base.videoClips.mapIndexed { i, c -> if (i == 0) c.copy(freezeMs = 1_000) else c })
        assertEquals(EditError.NotForFreezeFrame, err(EdgeTrim.apply(frozen, "a", TrimEdge.END, 100)))
        assertEquals(EditError.ClipNotFound("x"), err(EdgeTrim.apply(base, "x", TrimEdge.END, 100)))
        assertEquals(EditError.InvalidTrimRange, err(EdgeTrim.apply(base, "b", TrimEdge.END, 100)))
    }

    // ---------------- ripple delete ----------------
    @Test fun a_ripple_delete_pulls_later_audio_back_with_the_picture() {
        val t0 = TL.withAudio(base, listOf(TL.audio("early", 1_000, start = 500), TL.audio("late", 1_000, start = 5_000)))
        val t = ok(RippleDelete.remove(t0, "a"))
        assertEquals(listOf("b"), t.videoClips.map { it.id })
        assertEquals(listOf(0L, 1_000L), t.audioClips.map { it.startTimeMs }) // "early" sat inside the cut: it moves to where the cut was
    }

    @Test fun deleting_an_audio_clip_with_ripple_closes_its_lane() {
        val t0 = TL.withAudio(base, listOf(TL.audio("m1", 1_000, start = 0), TL.audio("m2", 1_000, start = 2_000)))
        val t = ok(RippleDelete.remove(t0, "m1"))
        assertEquals(listOf(1_000L), t.audioClips.map { it.startTimeMs })
    }

    @Test fun several_clips_go_in_one_edit() {
        val t0 = TL.withAudio(TL.timeline(TL.video("a", 1_000), TL.video("b", 1_000), TL.video("c", 1_000)), listOf(TL.audio("m", 500, start = 2_000)))
        val plain = ok(RippleDelete.removeAll(t0, listOf("a", "m"), ripple = false))
        assertEquals(listOf("b", "c"), plain.videoClips.map { it.id }); assertTrue(plain.audioClips.isEmpty())
        val rippled = ok(RippleDelete.removeAll(t0, listOf("a", "b"), ripple = true))
        assertEquals(0L, rippled.audioClips.single().startTimeMs)
        assertEquals(EditError.ClipNotFound("zz"), err(RippleDelete.removeAll(t0, listOf("a", "zz"), ripple = true)))
        assertEquals(EditError.NothingSelected, err(RippleDelete.removeAll(t0, emptyList(), ripple = false)))
    }

    // ---------------- snapping ----------------
    @Test fun edges_snap_to_nearby_clip_edges_and_the_playhead() {
        val points = Snapping.points(base, playheadMs = 1_234, excludeId = "b")
        assertEquals(listOf(0L, 1_234L, 4_000L), points)
        assertEquals(4_000L, Snapping.snap(3_950, points, thresholdMs = 100))
        assertEquals(3_800L, Snapping.snap(3_800, points, thresholdMs = 100))
        assertEquals(1_234L, Snapping.snap(1_300, points, thresholdMs = 100))
    }

    @Test fun a_moved_clip_snaps_by_its_start_or_else_its_end() {
        val points = listOf(0L, 4_000L)
        assertEquals(4_000L, Snapping.snapMove(4_050, 1_000, points, 100))
        assertEquals(3_000L, Snapping.snapMove(2_960, 1_000, points, 100)) // its end lands on 4 s
        assertEquals(2_000L, Snapping.snapMove(2_000, 1_000, points, 100))
    }

    // ---------------- through the editor (undo) ----------------
    @Test fun edge_trims_and_multi_deletes_are_single_undo_steps() {
        val editor = DefaultTimelineEditor(base)
        editor.trimEdge("a", TrimEdge.END, 500)
        editor.removeClips(listOf("a", "b"), ripple = false)
        assertTrue(editor.state.value.timeline.videoClips.isEmpty())
        editor.undo()
        assertEquals(6_500L, editor.state.value.timeline.videoClips[0].sourceEndMs)
        editor.undo()
        assertEquals(6_000L, editor.state.value.timeline.videoClips[0].sourceEndMs)
    }
}
