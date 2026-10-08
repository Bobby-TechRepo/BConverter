package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingStateTest {
    private val running = ProcessingState.Running(0.5f, "Encoding")
    private val terminals = listOf(
        ProcessingState.Completed, ProcessingState.Failed("x"), ProcessingState.Cancelled,
    )

    private fun reduce(s: ProcessingState, e: ProcessingEvent) = ProcessingTransitions.reduce(s, e)

    @Test fun idle_start_goes_to_running_at_zero() =
        assertEquals(ProcessingState.Running(0f, "Starting"), reduce(ProcessingState.Idle, ProcessingEvent.Start))

    @Test fun running_progress_updates_progress_and_message() =
        assertEquals(
            ProcessingState.Running(0.75f, "Muxing"),
            reduce(running, ProcessingEvent.Progress(0.75f, "Muxing")),
        )

    @Test fun running_complete_goes_to_completed() =
        assertEquals(ProcessingState.Completed, reduce(running, ProcessingEvent.Complete))

    @Test fun running_fail_goes_to_failed_with_message() =
        assertEquals(ProcessingState.Failed("Boom"), reduce(running, ProcessingEvent.Fail("Boom")))

    @Test fun running_cancel_goes_to_cancelled() =
        assertEquals(ProcessingState.Cancelled, reduce(running, ProcessingEvent.Cancel))

    @Test fun every_terminal_state_resets_to_idle() =
        terminals.forEach { assertEquals(ProcessingState.Idle, reduce(it, ProcessingEvent.Reset)) }

    @Test(expected = IllegalStateException::class)
    fun idle_cannot_complete() { reduce(ProcessingState.Idle, ProcessingEvent.Complete) }

    @Test(expected = IllegalStateException::class)
    fun idle_cannot_be_cancelled() { reduce(ProcessingState.Idle, ProcessingEvent.Cancel) }

    @Test(expected = IllegalStateException::class)
    fun running_cannot_start_again() { reduce(running, ProcessingEvent.Start) }

    @Test(expected = IllegalStateException::class)
    fun completed_cannot_receive_progress() {
        reduce(ProcessingState.Completed, ProcessingEvent.Progress(0.1f, "x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun progress_above_one_is_rejected() { ProcessingState.Running(1.1f, "x") }

    @Test(expected = IllegalArgumentException::class)
    fun negative_progress_is_rejected() { ProcessingState.Running(-0.1f, "x") }
}
