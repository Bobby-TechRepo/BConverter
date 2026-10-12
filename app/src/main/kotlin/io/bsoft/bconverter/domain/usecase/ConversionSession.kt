package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ProcessingEvent
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ProcessingTransitions
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngine
import io.bsoft.bconverter.media.api.MediaProcessingResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one conversion at a time and exposes it as a [ProcessingState] machine (spec §32–34).
 * On failure, cancellation (user or coroutine) it stops the engine, deletes the partial output and
 * releases the staged input, so the UI can always return to a valid state via [reset].
 */
class ConversionSession(
    private val engine: MediaEngine,
    private val resolver: InputResolver,
) {
    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    @Volatile private var activeJobId: String? = null

    /**
     * Throws [IllegalStateException], touching nothing, unless the session is idle.
     * With [releaseInput] false the caller keeps ownership of the staged input (e.g. to allow a retry).
     */
    suspend fun run(request: ConversionRequest, releaseInput: Boolean = true) {
        transition(ProcessingEvent.Start)
        activeJobId = request.jobId
        try {
            val result = coroutineScope {
                val progress = launch {
                    engine.observeProgress(request.jobId).collect { p ->
                        p.percentage?.let { transitionIfRunning(ProcessingEvent.Progress(it.coerceIn(0f, 1f), PROGRESS_MESSAGE)) }
                    }
                }
                try { engine.convert(request) } finally { progress.cancel() }
            }
            when (result) {
                is MediaProcessingResult.Success -> transition(ProcessingEvent.Complete)
                is MediaProcessingResult.Failure -> { discardOutput(request); transition(ProcessingEvent.Fail(FAILED_MESSAGE)) }
                MediaProcessingResult.Cancelled -> { discardOutput(request); transition(ProcessingEvent.Cancel) }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { engine.cancel(request.jobId) }
            discardOutput(request)
            transitionIfRunning(ProcessingEvent.Cancel)
            throw e
        } catch (e: Exception) {
            discardOutput(request)
            transitionIfRunning(ProcessingEvent.Fail(FAILED_MESSAGE))
        } finally {
            activeJobId = null
            if (releaseInput) resolver.release(request.input)
        }
    }

    /** Asks the engine to stop; [run] then finishes with [ProcessingState.Cancelled]. No-op when idle. */
    suspend fun cancel() {
        activeJobId?.let { engine.cancel(it) }
    }

    /** Returns a finished session to idle. Throws [IllegalStateException] while running. */
    fun reset() = transition(ProcessingEvent.Reset)

    private fun transition(event: ProcessingEvent) { _state.value = ProcessingTransitions.reduce(_state.value, event) }

    private fun transitionIfRunning(event: ProcessingEvent) { if (_state.value is ProcessingState.Running) transition(event) }

    private fun discardOutput(request: ConversionRequest) { request.outputFile.delete() }

    private companion object {
        const val PROGRESS_MESSAGE = "Converting"
        const val FAILED_MESSAGE = "The conversion failed. Please try again with different settings or another file."
    }
}
