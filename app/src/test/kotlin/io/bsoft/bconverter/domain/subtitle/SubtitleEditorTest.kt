package io.bsoft.bconverter.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleEditorTest {
    private fun cue(id: String, s: Long, e: Long, t: String = id) = SubtitleCue(id, s, e, t)
    private val track = SubtitleTrack("t", "Track", listOf(cue("a", 1000, 2000), cue("b", 3000, 4000), cue("c", 5000, 6000)))
    private fun ok(r: SubtitleEdit) = (r as SubtitleEdit.Ok).track
    private fun why(r: SubtitleEdit) = (r as SubtitleEdit.Rejected).reason
    private fun ids(t: SubtitleTrack) = t.cues.map { it.id }

    @Test fun adding_a_cue_keeps_cues_sorted_by_start() {
        val t = ok(SubtitleEditor.addCue(track, cue("n", 2500, 2800)))
        assertEquals(listOf("a", "n", "b", "c"), ids(t))
    }

    @Test fun adding_with_a_duplicate_id_is_rejected() = assertEquals(EditProblem.DuplicateId, why(SubtitleEditor.addCue(track, cue("a", 7000, 8000))))

    @Test fun overlapping_cues_are_allowed() = assertEquals(4, ok(SubtitleEditor.addCue(track, cue("n", 1500, 3500))).cues.size)

    @Test fun editing_text_changes_only_that_cue() {
        val t = ok(SubtitleEditor.editText(track, "b", "new"))
        assertEquals(listOf("a", "new", "c"), t.cues.map { it.text })
    }

    @Test fun editing_an_unknown_cue_is_rejected() = assertEquals(EditProblem.NoSuchCue, why(SubtitleEditor.editText(track, "zz", "x")))

    @Test fun moving_shifts_start_and_end_together_and_resorts() {
        val t = ok(SubtitleEditor.moveCue(track, "a", 2500))
        assertEquals(listOf("b", "a", "c"), ids(t)); assertEquals(3500L to 4500L, t.cues[1].startMs to t.cues[1].endMs)
    }

    @Test fun moving_before_zero_stops_at_zero_and_keeps_the_duration() {
        val c = ok(SubtitleEditor.moveCue(track, "a", -5000)).cues.first()
        assertEquals(0L to 1000L, c.startMs to c.endMs)
    }

    @Test fun retiming_sets_both_edges() {
        val c = ok(SubtitleEditor.retime(track, "a", 500, 2600)).cues.first()
        assertEquals(500L to 2600L, c.startMs to c.endMs)
    }

    @Test fun retiming_to_an_invalid_interval_is_rejected() {
        assertEquals(EditProblem.InvalidTiming, why(SubtitleEditor.retime(track, "a", 2000, 2000)))
        assertEquals(EditProblem.InvalidTiming, why(SubtitleEditor.retime(track, "a", -1, 2000)))
    }

    @Test fun duplicating_places_the_copy_right_after_the_original() {
        val t = ok(SubtitleEditor.duplicateCue(track, "a", "a2"))
        assertEquals(listOf("a", "a2", "b", "c"), ids(t))
        val copy = t.cues[1]; assertEquals(2000L to 3000L, copy.startMs to copy.endMs); assertEquals("a", copy.text)
    }

    @Test fun deleting_removes_the_cue() = assertEquals(listOf("a", "c"), ids(ok(SubtitleEditor.deleteCue(track, "b"))))

    @Test fun splitting_makes_two_cues_that_meet_at_the_split_point() {
        val t = ok(SubtitleEditor.splitCue(track, "a", 1400, "a2"))
        assertEquals(listOf("a", "a2", "b", "c"), ids(t))
        assertEquals(1000L to 1400L, t.cues[0].startMs to t.cues[0].endMs); assertEquals(1400L to 2000L, t.cues[1].startMs to t.cues[1].endMs)
    }

    @Test fun splitting_outside_the_cue_is_rejected() {
        assertEquals(EditProblem.InvalidTiming, why(SubtitleEditor.splitCue(track, "a", 1000, "x")))
        assertEquals(EditProblem.InvalidTiming, why(SubtitleEditor.splitCue(track, "a", 2000, "x")))
        assertEquals(EditProblem.InvalidTiming, why(SubtitleEditor.splitCue(track, "a", 9000, "x")))
    }

    @Test fun merging_joins_text_and_covers_both_intervals() {
        val t = ok(SubtitleEditor.mergeCues(track, "a", "b"))
        assertEquals(listOf("a", "c"), ids(t))
        assertEquals(1000L to 4000L, t.cues[0].startMs to t.cues[0].endMs); assertEquals("a\nb", t.cues[0].text)
    }

    @Test fun merging_works_whichever_order_the_ids_are_given() = assertEquals("a\nb", ok(SubtitleEditor.mergeCues(track, "b", "a")).cues[0].text)

    @Test fun merging_a_cue_with_itself_or_an_unknown_cue_is_rejected() {
        assertEquals(EditProblem.NoSuchCue, why(SubtitleEditor.mergeCues(track, "a", "zz")))
        assertEquals(EditProblem.InvalidTiming, why(SubtitleEditor.mergeCues(track, "a", "a")))
    }

    @Test fun the_track_offset_is_set_without_touching_the_cues() {
        val t = ok(SubtitleEditor.setOffset(track, -1500))
        assertEquals(-1500L, t.offsetMs); assertEquals(track.cues, t.cues)
        assertEquals(0L to 500L, t.effectiveCues().first().let { it.startMs to it.endMs }) // shown shifted, clamped at zero
    }

    @Test fun editing_never_changes_the_original_track() {
        SubtitleEditor.deleteCue(track, "a"); SubtitleEditor.addCue(track, cue("n", 0, 10))
        assertTrue(ids(track) == listOf("a", "b", "c"))
    }
}
