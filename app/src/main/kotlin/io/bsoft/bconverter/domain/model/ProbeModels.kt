package io.bsoft.bconverter.domain.model

sealed interface ProbeFailure {
    data class Input(val error: InputError) : ProbeFailure
    data object InvalidOutput : ProbeFailure
    data object NoMediaStreams : ProbeFailure
    data class ToolFailed(val exitCode: Int?) : ProbeFailure
}

sealed interface ProbeOutcome {
    /** [input] stays unreleased: the caller owns it (e.g. for the conversion that follows). */
    data class Success(val metadata: MediaMetadata, val input: ResolvedInput) : ProbeOutcome
    data class Failure(val failure: ProbeFailure) : ProbeOutcome
}
