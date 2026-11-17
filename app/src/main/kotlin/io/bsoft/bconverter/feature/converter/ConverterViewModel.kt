package io.bsoft.bconverter.feature.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository
import io.bsoft.bconverter.domain.usecase.AdvancedChoices
import io.bsoft.bconverter.domain.usecase.AdvancedOptions
import io.bsoft.bconverter.domain.usecase.AdvancedOptionsApplier
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.usecase.ConversionPresetFactory
import io.bsoft.bconverter.domain.usecase.ConversionRequestValidator
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.StorageCheck
import io.bsoft.bconverter.domain.usecase.StorageEstimator
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.media.api.ConversionRequest
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
 * Drives the converter flow (spec §17): pick → probe → configure → destination → convert → saved.
 * The conversion itself runs as a background export job that outlives this screen. The staged input is owned
 * here until the job is saved, the user starts over, or the screen is left (a still-running job then releases it).
 */
@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val prober: MediaProber,
    private val capabilities: CapabilitySource,
    private val resolver: InputResolver,
    private val staging: StagingDirectory,
    private val storage: StorageGuard,
    private val jobs: ExportJobLauncher,
) : ViewModel() {
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()
    private val _events = Channel<ConverterEvent>(Channel.BUFFERED)
    val events: Flow<ConverterEvent> = _events.receiveAsFlow()

    private var input: ResolvedInput? = null
    private var repository: MediaCapabilityRepository? = null
    private var statusJob: Job? = null
    private var activeJobId: String? = null

    // ---- input ----
    /** [uri] is null when the user dismissed the picker. */
    fun onInputPicked(uri: Uri?) {
        if (uri == null) return
        val stage = _state.value.stage
        if (stage == ConverterStage.Probing || stage == ConverterStage.Converting) return
        releaseInput()
        _state.value = ConverterUiState(stage = ConverterStage.Probing)
        viewModelScope.launch { open(uri) }
    }

    private suspend fun open(uri: Uri) {
        when (val outcome = prober.probe(MediaSource.UriSource(uri))) {
            is ProbeOutcome.Failure -> fail(messageFor(outcome.failure)) // the prober already released the input
            is ProbeOutcome.Success -> {
                val resolved = outcome.input
                if (!outcome.metadata.hasVideo) { resolver.release(resolved); fail(ConverterMessage.NoVideoStream); return }
                val repo = capabilities.repository()
                val containers = repo.getContainers()
                if (containers.isEmpty()) { resolver.release(resolved); fail(ConverterMessage.NoEncodersAvailable); return }
                input = resolved
                repository = repo
                _state.value = ConverterUiState(
                    stage = ConverterStage.Configure,
                    source = SourceInfo(resolved.displayName, resolved.sizeBytes, outcome.metadata, resolved.file),
                    containers = containers,
                )
                val container = if (ContainerFormat.MP4 in containers) ContainerFormat.MP4 else containers.first()
                applyForm(ConverterForm(ConversionPreset.BALANCED, container, null, null, defaultName(resolved.displayName)))
            }
        }
    }

    private fun fail(message: ConverterMessage) { _state.value = ConverterUiState(message = message) }

    private fun messageFor(failure: ProbeFailure): ConverterMessage = when (failure) {
        is ProbeFailure.Input -> ConverterMessage.InputProblem(failure.error)
        else -> ConverterMessage.ProbeFailed
    }

    private fun defaultName(displayName: String): String {
        val base = displayName.substringBeforeLast('.', displayName).ifBlank { "video" }
        return "$base-converted"
    }

    // ---- form ----
    private fun applyForm(form: ConverterForm) {
        val repo = repository ?: return
        val metadata = _state.value.source?.metadata ?: return
        val base = ConversionPresetFactory(repo).create(form.preset, metadata, form.container, form.videoCodec, form.audioCodec)
        val crf = (base?.video as? VideoPlan.Encode)?.settings?.codec?.let(repo::supportsCrf) ?: false
        val choices = base?.let { AdvancedChoices.of(it, metadata, crf) } ?: AdvancedChoices()
        // Overrides that the new codec/container no longer offers are dropped rather than producing an invalid request.
        val advanced = form.advanced.sanitized(choices)
        val settings = base?.let { AdvancedOptionsApplier.apply(it, advanced, metadata.videoStreams.firstOrNull()) }
        _state.update {
            it.copy(
                form = form.copy(advanced = advanced), settings = settings, choices = choices,
                videoCodecs = repo.getVideoCodecs(form.container), audioCodecs = repo.getAudioCodecs(form.container),
                issues = emptyList(),
            )
        }
    }

    fun onPresetSelected(preset: ConversionPreset) {
        // A preset is a fresh start: earlier overrides would silently fight it.
        _state.value.form?.let { applyForm(it.copy(preset = preset, advanced = AdvancedOptions())) }
    }

    fun onContainerSelected(container: ContainerFormat) {
        val form = _state.value.form ?: return
        val repo = repository ?: return
        applyForm(
            form.copy(
                container = container,
                videoCodec = form.videoCodec?.takeIf { it in repo.getVideoCodecs(container) },
                audioCodec = form.audioCodec?.takeIf { it in repo.getAudioCodecs(container) },
            ),
        )
    }

    fun onVideoCodecSelected(codec: VideoCodec?) {
        val form = _state.value.form ?: return
        if (codec != null && codec !in _state.value.videoCodecs) return
        applyForm(form.copy(videoCodec = codec))
    }

    fun onAudioCodecSelected(codec: AudioCodec?) {
        val form = _state.value.form ?: return
        if (codec != null && codec !in _state.value.audioCodecs) return
        applyForm(form.copy(audioCodec = codec))
    }

    fun onAdvancedChanged(options: AdvancedOptions) {
        _state.value.form?.let { applyForm(it.copy(advanced = options)) }
    }

    fun onOutputNameChanged(name: String) {
        val form = _state.value.form ?: return
        _state.update { it.copy(form = form.copy(outputName = name), issues = emptyList()) }
    }

    // ---- converting ----
    fun onStartRequested() {
        val s = _state.value
        val form = s.form ?: return
        val settings = s.settings ?: return
        val repo = repository ?: return
        val metadata = s.source?.metadata ?: return
        if (s.stage != ConverterStage.Configure) return
        val estimate = StorageEstimator.estimateConversion(settings, metadata, input?.sizeBytes ?: 0L)
        val storageIssue = (storage.check(estimate) as? StorageCheck.Insufficient)
            ?.let { ConversionIssue.InsufficientStorage(it.requiredBytes, it.availableBytes) }
        val issues = ConversionRequestValidator(repo).validate(settings, metadata, form.outputName) + listOfNotNull(storageIssue)
        _state.update { it.copy(issues = issues, message = null) }
        if (issues.isEmpty()) {
            _events.trySend(
                ConverterEvent.RequestDestination("${form.outputName.trim()}.${form.container.extension}", form.container.mimeType),
            )
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
        val temp = staging.newFile("$jobId.${settings.container.extension}")
        val request = ConversionRequest(jobId, resolved, source.metadata, temp, settings)
        _state.update { it.copy(stage = ConverterStage.Converting, message = null) }
        track(jobs.launch(ExportJob(jobId, source.displayName, ExportTask.Convert(request), destination)), jobId, SavedOutput(destination, settings.container.mimeType))
    }

    private fun track(status: StateFlow<JobStatus>, jobId: String, saved: SavedOutput) {
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
                JobStatus.Completed -> { releaseInput(); _state.update { it.copy(stage = ConverterStage.Completed, processing = ProcessingState.Idle, saved = saved) } }
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
        if (_state.value.stage != ConverterStage.Converting) return
        activeJobId?.let { jobs.cancel(it) }
    }

    fun onConvertAnother() {
        statusJob?.cancel()
        releaseInput()
        repository = null
        _state.value = ConverterUiState()
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
