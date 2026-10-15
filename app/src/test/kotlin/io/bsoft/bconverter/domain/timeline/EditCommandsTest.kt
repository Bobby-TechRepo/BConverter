package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditCommandsTest {
    private fun three() = TL.timeline(TL.video("a", 10_000), TL.video("b", 20_000), TL.video("c", 5_000))
    private fun withAudio() = TL.withAudio(three(), listOf(TL.audio("m", 5_000, sourceDuration = 10_000)))

    /** execute() changes the timeline, undo() restores it exactly, redo() reproduces the change. */
    private fun roundTrip(command: EditCommand, start: Timeline) {
        val after = ok(command.execute(start))
        assertNotEquals(start, after)
        assertEquals(start, command.undo(after))
        assertEquals(after, ok(command.redo(command.undo(after))))
    }

    @Test fun adding_a_video_clip() = roundTrip(AddVideoClipCommand(TL.video("n", 4_000), 1), three())
    @Test fun adding_an_audio_clip() = roundTrip(AddAudioClipCommand(TL.audio("n", 3_000), 2_000, "t1"), withAudio())
    @Test fun deleting_a_clip() = roundTrip(DeleteClipCommand("b"), three())
    @Test fun splitting_a_clip() = roundTrip(SplitClipCommand("b", 25_000, "n"), three())
    @Test fun trimming_a_clip() = roundTrip(TrimClipCommand("a", 2_000, 8_000), three())
    @Test fun moving_a_video_clip() = roundTrip(MoveClipCommand("a", 2), three())
    @Test fun moving_an_audio_clip() = roundTrip(MoveAudioClipCommand("m", 3_000), withAudio())
    @Test fun changing_speed() = roundTrip(ChangeSpeedCommand("a", 2f), three())
    @Test fun changing_volume() = roundTrip(ChangeVolumeCommand("m", 0.5f), withAudio())
    @Test fun replacing_a_clip() = roundTrip(ReplaceClipCommand("b", TL.source("new.mp4"), 8_000), three())

    @Test fun deleting_three_clips_down_to_two_and_back() {
        val command = DeleteClipCommand("b"); val start = three()
        val after = ok(command.execute(start))
        assertEquals(2, after.videoClips.size)
        assertEquals(3, command.undo(after).videoClips.size)
        assertEquals(2, ok(command.redo(command.undo(after))).videoClips.size)
    }

    @Test fun a_command_that_fails_reports_why_and_cannot_be_undone() {
        val command = DeleteClipCommand("x")
        assertEquals(EditError.ClipNotFound("x"), err(command.execute(three())))
        try { command.undo(three()); org.junit.Assert.fail("expected IllegalStateException") } catch (_: IllegalStateException) {}
    }

    @Test(expected = IllegalStateException::class) fun undoing_a_command_that_never_ran_is_a_programming_error() {
        TrimClipCommand("a", 0, 1).undo(three())
    }

    // ---- history ----
    private fun delete(id: String) = DeleteClipCommand(id)

    @Test fun a_fresh_history_has_nothing_to_undo_or_redo() {
        val h = CommandHistory()
        assertFalse(h.canUndo); assertFalse(h.canRedo); assertNull(h.undo(three())); assertNull(h.redo(three()))
    }

    @Test fun an_executed_command_can_be_undone_and_redone() {
        val h = CommandHistory(); val t0 = three()
        val t1 = ok(h.execute(delete("a"), t0))
        assertTrue(h.canUndo); assertEquals(t0, h.undo(t1)); assertTrue(h.canRedo); assertEquals(t1, h.redo(t0))
    }

    @Test fun undo_steps_back_through_several_commands() {
        val h = CommandHistory(); val t0 = three()
        val t1 = ok(h.execute(delete("a"), t0)); val t2 = ok(h.execute(delete("b"), t1))
        assertEquals(t1, h.undo(t2)); assertEquals(t0, h.undo(t1)); assertNull(h.undo(t0))
    }

    @Test fun a_new_command_clears_the_redo_stack() {
        val h = CommandHistory(); val t0 = three()
        val t1 = ok(h.execute(delete("a"), t0)); h.undo(t1)
        ok(h.execute(delete("b"), t0))
        assertFalse(h.canRedo)
    }

    @Test fun failed_commands_are_not_recorded() {
        val h = CommandHistory(); h.execute(delete("x"), three()); assertFalse(h.canUndo)
    }

    @Test fun the_oldest_commands_are_forgotten_beyond_the_limit() {
        val h = CommandHistory(limit = 2); val t0 = three()
        val t1 = ok(h.execute(delete("a"), t0)); val t2 = ok(h.execute(delete("b"), t1)); val t3 = ok(h.execute(delete("c"), t2))
        assertEquals(t2, h.undo(t3)); assertEquals(t1, h.undo(t2)); assertNull(h.undo(t1))
    }

    @Test fun clearing_empties_both_stacks() {
        val h = CommandHistory(); val t1 = ok(h.execute(delete("a"), three())); h.undo(t1); h.clear()
        assertFalse(h.canUndo); assertFalse(h.canRedo)
    }
}
