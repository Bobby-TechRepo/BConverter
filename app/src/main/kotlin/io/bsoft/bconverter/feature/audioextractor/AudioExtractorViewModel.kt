package io.bsoft.bconverter.feature.audioextractor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository
import io.bsoft.bconverter.domain.usecase.AudioExtractionPlanner
import io.bsoft.bconverter.domain.usecase.AudioExtractionValidator
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.StorageCheck
import io.bsoft.bconverter.domain.usecase.StorageEstimator
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.feature.converter.ConverterEvent
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.SourceInfo
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives audio extraction (spec §19): pick → probe → choose track/format → destination → extract → save.
 * Mirrors [io.bsoft.bconverter.feature.converter.ConverterViewModel]; the shared job/publish handling is a candidate
 * for extraction into a common collaborator once both flows are stable on device.
 */
@HiltViewModel
class AudioExtractorViewModel @Inject constructor(
    private val prober: MediaProber,
    private val capabilities: CapabilitySource,
    private val resolver: InputResolver,
    private val staging: StagingDirectory,
    private val storage: StorageGuard,
    private val jobs: ExportJobLauncher,
) : ViewModel() {
    private val _state = MutableStateFlow(AudioExtractorUiState())
    val state: StateFlow<AudioExtractorUiState> = _state.asStateFlow()
    private val _events = Channel<ConverterEvent>(Channel.BUFFERED)
    val events: Flow<ConverterEvent> = _events.receiveAsFlow()

    private var input: ResolvedInput? = null
    private var repository: MediaCapabilityRepository? = null
    private var planner: AudioExtractionPlanner? = null
    private var statusJob: Job? = null
    private var activeJobId: String? = null

    // ---- input ----
    /** [uri] is null when the user dismissed the picker. */
    fun onInputPicked(uri: Uri?) {
        if (uri == null) return
        val stage = _state.value.stage
        if (stage == ConverterStage.Probing || stage == ConverterStage.Extracting) return
        releaseInput()
        _state.value = AudioExtractorUiState(stage = ConverterStage.Probing)
        viewModelScope.launch { open(uri) }
    }

    private suspend fun open(uri: Uri) {
        when (val outcome = prober.probe(MediaSource.UriSource(uri))) {
            is ProbeOutcome.Failure -> fail(messageFor(outcome.failure)) // the prober already released the input
            is ProbeOutcome.Success -> {
                val resolved = outcome.input
                if (!outcome.metadata.hasAudio) { resolver.release(resolved); fail(ConverterMessage.NoAudioStream); return }
                val repo = capabilities.repository()
                val newPlanner = AudioExtractionPlanner(repo)
                val default = newPlanner.defaultSettings(outcome.metadata, 0)
                if (default == null) { resolver.release(resolved); fail(ConverterMessage.NoEncodersAvailable); return }
                input = resolved
                repository = repo
                planner = newPlanner
                _state.value = AudioExtractorUiState(
                    stage = ConverterStage.Configure,
                    source = SourceInfo(resolved.displayName, resolved.sizeBytes, outcome.metadata, resolved.file),
                )
                applyForm(ExtractorForm(0, default.format, preferCopy = true, outputName = defaultName(resolved.displayName)))
            }
        }
    }

    private fun fail(message: ConverterMessage) { _state.value = AudioExtractorUiState(message = message) }

    private fun messageFor(failure: ProbeFailure): ConverterMessage = when (failure) {
        is ProbeFailure.Input -> ConverterMessage.InputProblem(failure.error)
        else -> ConverterMessage.ProbeFailed
    }

    private fun defaultName(displayName: String): String =
        displayName.substringBeforeLast('.', displayName).ifBlank { "audio" } + "-audio"

    // ---- form ----
    private fun applyForm(form: ExtractorForm) {
        val planner = planner ?: return
        val metadata = _state.value.source?.metadata ?: return
        _state.update {
            it.copy(
                form = form,
                settings = planner.settingsFor(form.format, metadata, form.streamOrdinal, form.preferCopy),
                formats = planner.availableFormats(metadata, form.streamOrdinal),
                canCopy = planner.canCopy(form.format, metadata, form.streamOrdinal),
                issues = emptyList(),
            )
        }
    }

    fun onStreamSelected(ordinal: Int) {
        val form = _state.value.form ?: return
        val planner = planner ?: return
        val metadata = _state.value.source?.metadata ?: return
        if (ordinal !in metadata.audioStreams.indices) return
        val format = form.format.takeIf { it in planner.availableFormats(metadata, ordinal) }
            ?: planner.defaultSettings(metadata, ordinal)?.format
            ?: return
        applyForm(form.copy(streamOrdinal = ordinal, format = format))
    }

    fun onFormatSelected(format: AudioFormat) {
        val form = _state.value.form ?: return
        if (format !in _state.value.formats) return
        applyForm(form.copy(format = format))
    }

    fun onPreferCopyChanged(preferCopy: Boolean) {
        _state.value.form?.let { applyForm(it.copy(preferCopy = preferCopy)) }
    }

    fun onOutputNameChanged(name: String) {
        val form = _state.value.form ?: return
        _state.update { it.copy(form = form.copy(outputName = name), issues = emptyList()) }
    }

    // ---- extracting ----
    fun onStartRequested() {
        val s = _state.value
        val form = s.form ?: return
        val settings = s.settings ?: return
        val repo = repository ?: return
        val metadata = s.source?.metadata ?: return
        if (s.stage != ConverterStage.Configure) return
        val estimate = StorageEstimator.estimateExtraction(settings, metadata, input?.sizeBytes ?: 0L)
        val storageIssue = (storage.check(estimate) as? StorageCheck.Insufficient)
            ?.let { ConversionIssue.InsufficientStorage(it.requiredBytes, it.availableBytes) }
        val issues = AudioExtractionValidator(repo).validate(settings, metadata, form.outputName) + listOfNotNull(storageIssue)
        _state.update { it.copy(issues = issues, message = null) }
        if (issues.isEmpty()) {
            _events.trySend(ConverterEvent.RequestDestination("${form.outputName.trim()}.${form.format.extension}", form.format.mimeType))
        }
    }

    /** [destination] is null when the user dismissed the save dialog. */
    fun onDestinationChosen(destination: Uri?) {
        if (destination == null) return
        val s = _state.value
        val settings = s.settings ?: return
        val source = s.source ?: return
        val resolved = input ?: return
        if (s.stage != ConverterStage.Configure) return

        val jobId = UUID.randomUUID().toString()
        val temp = staging.newFile("$jobId.${settings.format.extension}")
        val request = AudioExtractionRequest(jobId, resolved, source.metadata, temp, settings)
        _state.update { it.copy(stage = ConverterStage.Extracting, message = null) }
        track(jobs.launch(ExportJob(jobId, source.displayName, ExportTask.Extract(request), destination)), jobId)
    }

    private fun track(status: StateFlow<JobStatus>, jobId: String) {
        activeJobId = jobId
        statusJob = viewModelScope.launch {
            val terminal = status
                .onEach { s ->
                    if (s is JobStatus.Running) {
                        _state.update { it.copy(processing = ProcessingState.Running(s.progress.coerceIn(0f, 1f), "Converting")) }
                    }
                }
                .first { it.isTerminal }
            jobs.finish(jobId)
            activeJobId = null
            when (terminal) {
                JobStatus.Completed -> { releaseInput(); _state.update { it.copy(stage = ConverterStage.Completed, processing = ProcessingState.Idle) } }
                is JobStatus.Failed ->
                    backToForm(if (terminal.reason == JobFailure.SaveFailed) ConverterMessage.SaveFailed else ConverterMessage.ConversionFailed)
                else -> backToForm(null) // cancelled
            }
        }
    }

    private fun backToForm(message: ConverterMessage?) {
        _state.update { it.copy(stage = ConverterStage.Configure, message = message, processing = ProcessingState.Idle) }
    }

    fun onCancel() {
        if (_state.value.stage != ConverterStage.Extracting) return
        activeJobId?.let { jobs.cancel(it) }
    }

    fun onExtractAnother() {
        statusJob?.cancel()
        releaseInput()
        repository = null
        planner = null
        _state.value = AudioExtractorUiState()
    }

    private fun releaseInput() {
        input?.let { resolver.release(it) }
        input = null
    }

    override fun onCleared() {
        // A job still running keeps going in the background and releases the input itself when it finishes.
        val running = activeJobId
        if (running != null) jobs.detach(running) else releaseInput()
    }
}
