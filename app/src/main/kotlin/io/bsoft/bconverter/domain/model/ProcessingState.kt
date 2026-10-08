package io.bsoft.bconverter.domain.model

sealed interface ProcessingState {
    data object Idle : ProcessingState

    data class Running(val progress: Float, val message: String) : ProcessingState {
        init { require(progress in 0f..1f) { "progress must be within 0..1, was $progress" } }
    }

    data object Completed : ProcessingState
    data class Failed(val message: String) : ProcessingState
    data object Cancelled : ProcessingState
}

sealed interface ProcessingEvent {
    data object Start : ProcessingEvent
    data class Progress(val fraction: Float, val message: String) : ProcessingEvent
    data object Complete : ProcessingEvent
    data class Fail(val message: String) : ProcessingEvent
    data object Cancel : ProcessingEvent
    data object Reset : ProcessingEvent
}

/** Pure state machine; invalid transitions throw [IllegalStateException]. */
object ProcessingTransitions {
    fun reduce(state: ProcessingState, event: ProcessingEvent): ProcessingState = when {
        state is ProcessingState.Idle && event is ProcessingEvent.Start ->
            ProcessingState.Running(0f, "Starting")
        state is ProcessingState.Running && event is ProcessingEvent.Progress ->
            ProcessingState.Running(event.fraction, event.message)
        state is ProcessingState.Running && event is ProcessingEvent.Complete -> ProcessingState.Completed
        state is ProcessingState.Running && event is ProcessingEvent.Fail -> ProcessingState.Failed(event.message)
        state is ProcessingState.Running && event is ProcessingEvent.Cancel -> ProcessingState.Cancelled
        (state is ProcessingState.Completed || state is ProcessingState.Failed ||
            state is ProcessingState.Cancelled) && event is ProcessingEvent.Reset -> ProcessingState.Idle
        else -> throw IllegalStateException("Invalid transition: $event from $state")
    }
}
