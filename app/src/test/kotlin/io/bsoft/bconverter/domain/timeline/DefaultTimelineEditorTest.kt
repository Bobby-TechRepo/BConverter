package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTimelineEditorTest {
    private fun three() = TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000), TL.video("c", 5_000))
    private fun editor(t: Timeline = three(), limit: Int = 100) = DefaultTimelineEditor(t, SequentialIds(), limit)
    private val DefaultTimelineEditor.timeline get() = state.value.timeline
    private val DefaultTimelineEditor.ids get() = timeline.videoClips.map { it.id }

    // ---- state ----
    @Test fun starts_with_the_given_timeline_nothing_selected_and_no_history() {
        val e = editor(); val s = e.state.value
        assertEquals(three(), s.timeline); assertNull(s.selectedClipId); assertEquals(0L, s.playheadMs)
        assertFalse(s.canUndo); assertFalse(s.canRedo)
    }

    // ---- selection ----
    @Test fun an_existing_clip_can_be_selected_and_deselected() {
        val e = editor()
        assertTrue(e.select("b")); assertEquals("b", e.state.value.selectedClipId)
        assertTrue(e.select(null)); assertNull(e.state.value.selectedClipId)
    }

    @Test fun selecting_an_unknown_clip_is_refused_and_keeps_the_selection() {
        val e = editor(); e.select("a")
        assertFalse(e.select("x")); assertEquals("a", e.state.value.selectedClipId)
    }

    @Test fun removing_the_selected_clip_clears_the_selection_but_other_removals_keep_it() {
        val e = editor(); e.select("b")
        e.removeClip("a"); assertEquals("b", e.state.value.selectedClipId)
        e.removeClip("b"); assertNull(e.state.value.selectedClipId)
    }

    // ---- playhead ----
    @Test fun the_playhead_is_kept_within_the_timeline() {
        val e = editor()
        e.seekTo(12_345); assertEquals(12_345L, e.state.value.playheadMs)
        e.seekTo(-5); assertEquals(0L, e.state.value.playheadMs)
        e.seekTo(99_999); assertEquals(35_000L, e.state.value.playheadMs)
    }

    @Test fun the_playhead_follows_a_shrinking_timeline() {
        val e = editor(); e.seekTo(35_000); e.removeClip("c")
        assertEquals(30_000L, e.state.value.playheadMs)
    }

    // ---- editing ----
    @Test fun a_successful_edit_updates_the_timeline_and_enables_undo() {
        val e = editor(); ok(e.splitClip("a", 4_000))
        assertEquals(listOf("a", "id-1", "b", "c"), e.ids); assertTrue(e.state.value.canUndo)
    }

    @Test fun a_failed_edit_changes_nothing_and_records_nothing() {
        val e = editor()
        assertEquals(EditError.ClipNotFound("x"), err(e.removeClip("x")))
        assertEquals(three(), e.timeline); assertFalse(e.state.value.canUndo)
    }

    @Test fun every_operation_is_available_through_the_editor() {
        val e = editor()
        ok(e.trimClip("a", 2_000, 8_000)); ok(e.changeSpeed("b", 2f)); ok(e.moveVideoClip("c", 0))
        ok(e.insertVideoClip(TL.video("n", 3_000), 1)); ok(e.insertAudioClip(TL.audio("m", 5_000), 1_000))
        ok(e.changeVolume("m", 0.5f)); ok(e.moveAudioClip("m", 2_000)); ok(e.replaceClip("n", TL.source("x.mp4"), 6_000))
        assertEquals(listOf("c", "n", "a", "b"), e.ids)
        assertEquals(2_000L, e.timeline.findClip("m")!!.startTimeMs)
    }

    @Test fun new_audio_tracks_get_generated_ids() {
        val e = editor(); ok(e.insertAudioClip(TL.audio("m"), 0))
        assertEquals("id-1", e.timeline.audioTracks.single().id)
    }

    @Test fun splitting_at_the_playhead_splits_the_selected_clip() {
        val e = editor(); e.select("b"); e.seekTo(25_000)
        ok(e.splitSelectedAtPlayhead())
        assertEquals(listOf("a", "b", "id-1", "c"), e.ids)
    }

    @Test fun splitting_needs_a_selection_and_a_playhead_inside_the_clip() {
        val e = editor()
        assertEquals(EditError.NothingSelected, err(e.splitSelectedAtPlayhead()))
        e.select("b"); e.seekTo(5_000)
        assertEquals(EditError.SplitOutsideClip, err(e.splitSelectedAtPlayhead()))
    }

    // ---- undo / redo ----
    @Test fun undo_and_redo_walk_through_the_edits() {
        val e = editor(); ok(e.splitClip("a", 4_000))
        assertTrue(e.undo()); assertEquals(three(), e.timeline); assertTrue(e.state.value.canRedo); assertFalse(e.state.value.canUndo)
        assertTrue(e.redo()); assertEquals(listOf("a", "id-1", "b", "c"), e.ids)
    }

    @Test fun a_new_edit_after_undo_discards_the_redo_history() {
        val e = editor(); ok(e.removeClip("a")); e.undo(); ok(e.removeClip("b"))
        assertFalse(e.state.value.canRedo); assertFalse(e.redo())
    }

    @Test fun undo_and_redo_with_nothing_to_do_report_false() { val e = editor(); assertFalse(e.undo()); assertFalse(e.redo()) }

    @Test fun undo_clears_a_selection_whose_clip_no_longer_exists() {
        val e = editor(); ok(e.splitClip("a", 4_000)); e.select("id-1"); e.undo()
        assertNull(e.state.value.selectedClipId)
    }

    @Test fun the_history_limit_applies_to_the_editor() {
        val e = editor(limit = 1); ok(e.removeClip("a")); ok(e.removeClip("b"))
        assertTrue(e.undo()); assertFalse(e.undo())
    }
}
