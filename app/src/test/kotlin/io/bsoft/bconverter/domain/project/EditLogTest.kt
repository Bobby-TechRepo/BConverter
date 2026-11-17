package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.overlay.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditLogTest {
    private fun snap(vararg ids: String) = ContentSnapshot(emptyList(), ids.map { TextOverlay(it, "x", 0, 1000) })
    private val empty = ContentSnapshot.Empty

    @Test fun starts_with_nothing_to_undo_or_redo() {
        val l = EditLog(); assertFalse(l.canUndo); assertFalse(l.canRedo); assertNull(l.undo(empty)); assertNull(l.redo(empty))
    }
    @Test fun content_edit_is_undone_to_the_snapshot_before_it_and_redone_to_the_one_after() {
        val l = EditLog(); l.recordContent(snap())          // snap() -> snap("a")
        assertEquals(EditStep.Content(snap()), l.undo(snap("a")))
        assertTrue(l.canRedo); assertFalse(l.canUndo)
        assertEquals(EditStep.Content(snap("a")), l.redo(snap()))
        assertTrue(l.canUndo); assertFalse(l.canRedo)
    }
    @Test fun steps_come_back_in_the_order_the_edits_were_made_across_kinds() {
        val l = EditLog(); l.recordTimeline(); l.recordContent(snap()); l.recordTimeline()
        assertEquals(EditStep.Timeline, l.undo(snap("a")))
        assertEquals(EditStep.Content(snap()), l.undo(snap("a")))
        assertEquals(EditStep.Timeline, l.undo(snap()))
        assertNull(l.undo(snap()))
        assertEquals(EditStep.Timeline, l.redo(snap())); assertEquals(EditStep.Content(snap("a")), l.redo(snap())); assertEquals(EditStep.Timeline, l.redo(snap("a")))
    }
    @Test fun a_new_edit_clears_redo_of_either_kind() {
        val l = EditLog(); l.recordContent(snap()); l.undo(snap("a")); assertTrue(l.canRedo)
        l.recordTimeline(); assertFalse(l.canRedo)
        l.undo(snap()); assertTrue(l.canRedo); l.recordContent(snap()); assertFalse(l.canRedo)
    }
    @Test fun several_content_edits_undo_one_by_one() {
        val l = EditLog(); l.recordContent(snap()); l.recordContent(snap("a")); l.recordContent(snap("a", "b"))
        assertEquals(EditStep.Content(snap("a", "b")), l.undo(snap("a", "b", "c")))
        assertEquals(EditStep.Content(snap("a")), l.undo(snap("a", "b")))
        assertEquals(EditStep.Content(snap()), l.undo(snap("a")))
    }
    @Test fun the_oldest_steps_are_dropped_past_the_limit_and_content_stays_aligned() {
        val l = EditLog(limit = 3)
        l.recordContent(snap("1")); l.recordTimeline(); l.recordContent(snap("2")); l.recordContent(snap("3"))
        // the first content edit is gone; three steps remain: timeline, content(2), content(3)
        assertEquals(EditStep.Content(snap("3")), l.undo(snap())); assertEquals(EditStep.Content(snap("2")), l.undo(snap()))
        assertEquals(EditStep.Timeline, l.undo(snap())); assertFalse(l.canUndo)
    }
    @Test fun clear_forgets_everything() {
        val l = EditLog(); l.recordTimeline(); l.recordContent(snap()); l.undo(snap()); l.clear()
        assertFalse(l.canUndo); assertFalse(l.canRedo)
    }
}
