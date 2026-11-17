package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOperationsTest {
    private val ops = TimelineOperations
    private fun three() = TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000), TL.video("c", 5_000))
    private fun ids(t: Timeline) = t.videoClips.map { it.id }
    private fun starts(t: Timeline) = t.videoClips.map { it.startTimeMs }
    private fun laneIds(t: Timeline) = t.audioTracks.map { lane -> lane.clips.map { it.id } }

    // ================= split =================
    @Test fun splitting_a_60_second_clip_at_20_seconds_gives_two_clips() {
        val t = ok(ops.split(TL.timeline(TL.video("a", 60_000)), "a", 20_000, "n"))
        assertEquals(listOf("a", "n"), ids(t))
        assertEquals(listOf(0L, 20_000L), starts(t)); assertEquals(listOf(20_000L, 40_000L), t.videoClips.map { it.durationMs })
    }

    @Test fun the_two_halves_cover_the_source_without_gap_or_overlap() {
        val (first, second) = ok(ops.split(TL.timeline(TL.video("a", 60_000)), "a", 20_000, "n")).videoClips
        assertEquals(0L, first.sourceStartMs); assertEquals(20_000L, first.sourceEndMs)
        assertEquals(20_000L, second.sourceStartMs); assertEquals(60_000L, second.sourceEndMs)
    }

    @Test fun splitting_a_later_clip_uses_timeline_positions() {
        val t = ok(ops.split(three(), "b", 25_000, "n"))
        assertEquals(listOf("a", "b", "n", "c"), ids(t)); assertEquals(listOf(0L, 10_000L, 25_000L, 30_000L), starts(t))
    }

    @Test fun splitting_a_sped_up_clip_splits_the_source_proportionally() {
        val (first, second) = ok(ops.split(TL.timeline(TL.video("a", 60_000, speed = 2f)), "a", 10_000, "n")).videoClips
        assertEquals(20_000L, first.sourceEndMs); assertEquals(10_000L, first.durationMs)
        assertEquals(20_000L, second.sourceStartMs); assertEquals(20_000L, second.durationMs)
    }

    @Test fun splitting_keeps_the_total_duration_and_later_clips_in_place() {
        val after = ok(ops.split(three(), "a", 4_000, "n"))
        assertEquals(35_000L, after.durationMs); assertEquals(30_000L, after.videoClips.last().startTimeMs)
    }

    @Test fun split_positions_outside_the_clip_are_rejected() {
        val t = TL.timeline(TL.video("a", 60_000))
        listOf(0L, 60_000L, -1L, 60_001L).forEach { assertEquals("$it", EditError.SplitOutsideClip, err(ops.split(t, "a", it, "n"))) }
        assertEquals(EditError.SplitOutsideClip, err(ops.split(three(), "b", 10_000, "n"))) // exactly at its start
        assertEquals(EditError.SplitOutsideClip, err(ops.split(three(), "b", 30_000, "n"))) // exactly at its end
    }

    @Test fun splitting_an_unknown_clip_fails() = assertEquals(EditError.ClipNotFound("x"), err(ops.split(three(), "x", 1, "n")))

    @Test fun an_audio_clip_splits_into_two_clips_on_the_same_track() {
        val t = TL.withAudio(Timeline(), listOf(TL.audio("m", 6_000, start = 2_000)))
        val after = ok(ops.split(t, "m", 5_000, "n"))
        assertEquals(listOf(listOf("m", "n")), laneIds(after))
        val (first, second) = after.audioTracks.single().clips
        assertEquals(2_000L, first.startTimeMs); assertEquals(3_000L, first.durationMs)
        assertEquals(5_000L, second.startTimeMs); assertEquals(3_000L, second.sourceStartMs); assertEquals(6_000L, second.sourceEndMs)
    }

    // ================= trim =================
    @Test fun trimming_shortens_the_clip_and_pulls_later_clips_forward() {
        val t = ok(ops.trim(three(), "a", 2_000, 8_000))
        assertEquals(6_000L, t.videoClips[0].durationMs); assertEquals(listOf(0L, 6_000L, 26_000L), starts(t))
    }

    @Test fun invalid_trim_ranges_are_rejected() {
        val t = TL.timeline(TL.video("a", 10_000))
        listOf(5_000L to 5_000L, -1L to 5_000L, 0L to 10_001L, 6_000L to 5_000L).forEach { (s, e) ->
            assertEquals("$s..$e", EditError.InvalidTrimRange, err(ops.trim(t, "a", s, e)))
        }
    }

    @Test fun a_trimmed_clip_can_be_extended_again_within_its_source() {
        val t = TL.timeline(TL.video("x", 6_000, sourceStart = 2_000, sourceDuration = 10_000))
        assertEquals(10_000L, ok(ops.trim(t, "x", 0, 10_000)).videoClips.single().durationMs)
    }

    @Test fun trimming_a_sped_up_clip_uses_source_coordinates() {
        val t = TL.timeline(TL.video("x", 10_000, speed = 2f, sourceDuration = 20_000))
        assertEquals(10_000L, ok(ops.trim(t, "x", 0, 20_000)).videoClips.single().durationMs)
    }

    @Test fun trimming_audio_cannot_run_into_the_next_clip_on_its_track() {
        val t = TL.withAudio(Timeline(), listOf(TL.audio("m", 5_000, sourceDuration = 10_000), TL.audio("n", 3_000, start = 6_000)))
        assertEquals(EditError.AudioOverlap, err(ops.trim(t, "m", 0, 8_000)))
        assertEquals(6_000L, ok(ops.trim(t, "m", 0, 6_000)).audioClips.first().durationMs)
        assertEquals(2_000L, ok(ops.trim(t, "m", 1_000, 3_000)).audioClips.first().durationMs)
    }

    @Test fun trimming_an_unknown_clip_fails() = assertEquals(EditError.ClipNotFound("x"), err(ops.trim(three(), "x", 0, 1)))

    // ================= speed =================
    @Test fun speed_changes_recalculate_the_duration() {
        val t = TL.timeline(TL.video("a", 8_000))
        mapOf(0.25f to 32_000L, 0.5f to 16_000L, 1f to 8_000L, 2f to 4_000L, 4f to 2_000L).forEach { (speed, expected) ->
            assertEquals("x$speed", expected, ok(ops.changeSpeed(t, "a", speed)).videoClips.single().durationMs)
        }
    }

    @Test fun a_speed_change_shifts_the_following_clips() {
        val t = ok(ops.changeSpeed(three(), "a", 2f))
        assertEquals(listOf(0L, 5_000L, 25_000L), starts(t))
    }

    @Test fun invalid_speeds_are_rejected() {
        listOf(0f, -1f, 0.1f, 5f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals("$it", EditError.InvalidSpeed, err(ops.changeSpeed(three(), "a", it)))
        }
    }

    @Test fun unknown_clips_have_no_speed() {
        val t = TL.withAudio(three(), listOf(TL.audio("m")))
        assertEquals(EditError.ClipNotFound("x"), err(ops.changeSpeed(t, "x", 2f)))
    }

    // ================= remove =================
    @Test fun removing_the_first_clip_pulls_the_rest_forward() {
        val t = ok(ops.remove(three(), "a")); assertEquals(listOf("b", "c"), ids(t)); assertEquals(listOf(0L, 20_000L), starts(t))
    }

    @Test fun removing_a_middle_clip_closes_the_gap() {
        val t = ok(ops.remove(three(), "b")); assertEquals(listOf("a", "c"), ids(t)); assertEquals(listOf(0L, 10_000L), starts(t))
    }

    @Test fun removing_the_last_clip_leaves_the_rest_untouched() {
        val t = ok(ops.remove(three(), "c")); assertEquals(listOf("a", "b"), ids(t)); assertEquals(listOf(0L, 10_000L), starts(t))
    }

    @Test fun removing_the_only_clip_leaves_an_empty_timeline() {
        val t = ok(ops.remove(TL.timeline(TL.video("a")), "a")); assertTrue(t.videoClips.isEmpty()); assertEquals(0L, t.durationMs)
    }

    @Test fun removing_the_last_audio_clip_of_a_track_removes_the_track() {
        val t = TL.withAudio(Timeline(), listOf(TL.audio("m")), listOf(TL.audio("n")))
        assertEquals(listOf(listOf("n")), laneIds(ok(ops.remove(t, "m"))))
    }

    @Test fun removing_an_unknown_clip_fails() = assertEquals(EditError.ClipNotFound("x"), err(ops.remove(three(), "x")))

    // ================= video insert / move =================
    @Test fun a_video_clip_can_be_inserted_at_the_start_middle_or_end() {
        assertEquals(listOf("n", "a", "b", "c"), ids(ok(ops.insertVideo(three(), TL.video("n", 4_000), 0))))
        assertEquals(listOf("a", "n", "b", "c"), ids(ok(ops.insertVideo(three(), TL.video("n", 4_000), 1))))
        val end = ok(ops.insertVideo(three(), TL.video("n", 4_000), 3))
        assertEquals(listOf("a", "b", "c", "n"), ids(end)); assertEquals(35_000L, end.videoClips.last().startTimeMs)
    }

    @Test fun inserting_outside_the_track_or_reusing_an_id_fails() {
        assertEquals(EditError.InvalidIndex, err(ops.insertVideo(three(), TL.video("n"), -1)))
        assertEquals(EditError.InvalidIndex, err(ops.insertVideo(three(), TL.video("n"), 4)))
        assertEquals(EditError.DuplicateId, err(ops.insertVideo(three(), TL.video("a"), 0)))
    }

    @Test fun moving_a_clip_forward() {
        val t = ok(ops.moveVideo(three(), "a", 2)); assertEquals(listOf("b", "c", "a"), ids(t)); assertEquals(listOf(0L, 20_000L, 25_000L), starts(t))
    }

    @Test fun moving_a_clip_backward_and_to_the_beginning() {
        assertEquals(listOf("c", "a", "b"), ids(ok(ops.moveVideo(three(), "c", 0))))
        assertEquals(listOf("b", "a", "c"), ids(ok(ops.moveVideo(three(), "b", 0))))
    }

    @Test fun moving_a_clip_to_its_own_position_changes_nothing() = assertEquals(three(), ok(ops.moveVideo(three(), "b", 1)))

    @Test fun invalid_move_targets_are_rejected() {
        assertEquals(EditError.InvalidIndex, err(ops.moveVideo(three(), "a", -1)))
        assertEquals(EditError.InvalidIndex, err(ops.moveVideo(three(), "a", 3)))
        assertEquals(EditError.ClipNotFound("x"), err(ops.moveVideo(three(), "x", 0)))
        assertEquals(EditError.WrongClipType, err(ops.moveVideo(TL.withAudio(three(), listOf(TL.audio("m"))), "m", 0)))
    }

    // ================= audio insert / move / volume =================
    private val empty = Timeline()

    @Test fun the_first_audio_clip_creates_a_track_at_its_start_time() {
        val t = ok(ops.insertAudio(empty, TL.audio("m", 5_000), 2_000, "t1"))
        assertEquals(listOf("t1"), t.audioTracks.map { it.id }); assertEquals(2_000L, t.audioClips.single().startTimeMs)
    }

    @Test fun a_clip_that_fits_after_another_shares_its_track() {
        val t = ok(ops.insertAudio(ok(ops.insertAudio(empty, TL.audio("m", 5_000), 0, "t1")), TL.audio("n", 3_000), 5_000, "t2"))
        assertEquals(listOf(listOf("m", "n")), laneIds(t))
    }

    @Test fun overlapping_audio_goes_to_a_new_track() {
        val t = ok(ops.insertAudio(ok(ops.insertAudio(empty, TL.audio("m", 5_000), 0, "t1")), TL.audio("n", 3_000), 4_000, "t2"))
        assertEquals(listOf("t1", "t2"), t.audioTracks.map { it.id }); assertEquals(listOf(listOf("m"), listOf("n")), laneIds(t))
    }

    @Test fun invalid_audio_inserts_are_rejected() {
        assertEquals(EditError.InvalidStartTime, err(ops.insertAudio(empty, TL.audio("m"), -1, "t1")))
        assertEquals(EditError.DuplicateId, err(ops.insertAudio(three(), TL.audio("a"), 0, "t1")))
    }

    private val lanes = TL.withAudio(
        Timeline(), listOf(TL.audio("m", 5_000), TL.audio("n", 3_000, start = 6_000)), listOf(TL.audio("o", 2_000, start = 10_000)),
    )

    @Test fun moving_audio_changes_its_start_on_the_same_track() =
        assertEquals(3_000L, ok(ops.moveAudio(TL.withAudio(Timeline(), listOf(TL.audio("m"))), "m", 3_000)).audioClips.single().startTimeMs)

    @Test fun moving_audio_onto_a_neighbour_on_the_same_track_is_rejected() {
        assertEquals(EditError.AudioOverlap, err(ops.moveAudio(lanes, "m", 4_000)))
        assertEquals(1_000L, ok(ops.moveAudio(lanes, "m", 1_000)).findClip("m")!!.startTimeMs) // ends exactly where n starts
    }

    @Test fun audio_can_move_to_another_track_and_empty_tracks_disappear() {
        val t = ok(ops.moveAudio(lanes, "o", 10_000, trackIndex = 0)) // free space after n on the first track
        assertEquals(1, t.audioTracks.size); assertEquals(listOf("m", "n", "o"), t.audioTracks.single().clips.map { it.id })
    }

    @Test fun invalid_audio_moves_are_rejected() {
        assertEquals(EditError.InvalidStartTime, err(ops.moveAudio(lanes, "m", -1)))
        assertEquals(EditError.InvalidIndex, err(ops.moveAudio(lanes, "m", 0, trackIndex = 5)))
        assertEquals(EditError.WrongClipType, err(ops.moveAudio(TL.withAudio(three(), listOf(TL.audio("m"))), "a", 0)))
        assertEquals(EditError.ClipNotFound("x"), err(ops.moveAudio(lanes, "x", 0)))
    }

    @Test fun volume_within_range_is_applied() {
        listOf(0f, 1f, 2f).forEach { volume ->
            val clip = ok(ops.changeVolume(lanes, "m", volume)).findClip("m") as AudioClip
            assertEquals(volume, clip.volume, 0f)
        }
    }

    @Test fun invalid_volumes_and_clip_types_are_rejected() {
        listOf(-0.1f, 2.5f, Float.NaN).forEach { assertEquals("$it", EditError.InvalidVolume, err(ops.changeVolume(lanes, "m", it))) }
        assertEquals(EditError.WrongClipType, err(ops.changeVolume(TL.withAudio(three(), listOf(TL.audio("m"))), "a", 1f)))
        assertEquals(EditError.ClipNotFound("x"), err(ops.changeVolume(lanes, "x", 1f)))
    }

    // ================= replace =================
    @Test fun replacing_a_video_clip_keeps_its_place_and_speed() {
        val base = ok(ops.changeSpeed(three(), "b", 2f))
        val t = ok(ops.replace(base, "b", TL.source("new.mp4"), 8_000))
        val b = t.videoClips[1]
        assertEquals("b", b.id); assertEquals(TL.source("new.mp4"), b.source); assertEquals(0L, b.sourceStartMs); assertEquals(8_000L, b.sourceEndMs)
        assertEquals(4_000L, b.durationMs); assertEquals(14_000L, t.videoClips[2].startTimeMs)
    }

    @Test fun replacing_audio_keeps_its_start_volume_and_track() {
        val t = TL.withAudio(Timeline(), listOf(TL.audio("m", 5_000, start = 2_000, volume = 0.5f)))
        val replaced = ok(ops.replace(t, "m", TL.source("new.mp3"), 7_000)).audioClips.single()
        assertEquals(2_000L, replaced.startTimeMs); assertEquals(0.5f, replaced.volume, 0f); assertEquals(7_000L, replaced.durationMs)
    }

    @Test fun replacement_audio_that_runs_into_a_neighbour_is_rejected() {
        val t = TL.withAudio(Timeline(), listOf(TL.audio("m", 5_000), TL.audio("n", 3_000, start = 6_000)))
        assertEquals(EditError.AudioOverlap, err(ops.replace(t, "m", TL.source("new.mp3"), 9_000)))
    }

    @Test fun replacement_needs_a_positive_duration_and_a_known_clip() {
        assertEquals(EditError.InvalidSourceDuration, err(ops.replace(three(), "a", TL.source("n"), 0)))
        assertEquals(EditError.ClipNotFound("x"), err(ops.replace(three(), "x", TL.source("n"), 1_000)))
    }
}
