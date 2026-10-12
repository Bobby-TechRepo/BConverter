package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ProcessingEvent
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ProcessingTransitions
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngine
import io.bsoft.bconverter.media.api.MediaProcessingResult
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one conversion or audio extraction at a time as a [ProcessingState] machine (spec §32–34).
 * On failure, cancellation (user or coroutine) it stops the engine, deletes the partial output and optionally
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
    suspend fun run(request: ConversionRequest, releaseInput: Boolean = true) =
        runJob(request.jobId, request.input, request.outputFile, releaseInput) { engine.convert(request) }

    suspend fun runExtraction(request: AudioExtractionRequest, releaseInput: Boolean = true) =
        runJob(request.jobId, request.input, request.outputFile, releaseInput) { engine.extractAudio(request) }

    private suspend fun runJob(
        jobId: String,
        input: ResolvedInput,
        outputFile: File,
        releaseInput: Boolean,
        execute: suspend () -> MediaProcessingResult,
    ) {
        transition(ProcessingEvent.Start)
        activeJobId = jobId
        try {
            val result = coroutineScope {
                val progress = launch {
                    engine.observeProgress(jobId).collect { p ->
                        p.percentage?.let { transitionIfRunning(ProcessingEvent.Progress(it.coerceIn(0f, 1f), PROGRESS_MESSAGE)) }
                    }
                }
                try { execute() } finally { progress.cancel() }
            }
            when (result) {
                is MediaProcessingResult.Success -> transition(ProcessingEvent.Complete)
                is MediaProcessingResult.Failure -> { outputFile.delete(); transition(ProcessingEvent.Fail(FAILED_MESSAGE)) }
                MediaProcessingResult.Cancelled -> { outputFile.delete(); transition(ProcessingEvent.Cancel) }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { engine.cancel(jobId) }
            outputFile.delete()
            transitionIfRunning(ProcessingEvent.Cancel)
            throw e
        } catch (e: Exception) {
            outputFile.delete()
            transitionIfRunning(ProcessingEvent.Fail(FAILED_MESSAGE))
        } finally {
            activeJobId = null
            if (releaseInput) resolver.release(input)
        }
    }

    /** Asks the engine to stop; the running job then finishes with [ProcessingState.Cancelled]. No-op when idle. */
    suspend fun cancel() {
        activeJobId?.let { engine.cancel(it) }
    }

    /** Returns a finished session to idle. Throws [IllegalStateException] while running. */
    fun reset() = transition(ProcessingEvent.Reset)

    private fun transition(event: ProcessingEvent) { _state.value = ProcessingTransitions.reduce(_state.value, event) }

    private fun transitionIfRunning(event: ProcessingEvent) { if (_state.value is ProcessingState.Running) transition(event) }

    private companion object {
        const val PROGRESS_MESSAGE = "Converting"
        const val FAILED_MESSAGE = "The conversion failed. Please try again with different settings or another file."
    }
}
