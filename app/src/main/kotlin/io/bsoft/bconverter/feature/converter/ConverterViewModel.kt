package io.bsoft.bconverter.feature.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.repository.PublishResult
import io.bsoft.bconverter.domain.usecase.ConversionPresetFactory
import io.bsoft.bconverter.domain.usecase.ConversionRequestValidator
import io.bsoft.bconverter.domain.usecase.ConversionSession
import io.bsoft.bconverter.media.api.ConversionRequest
import io.bsoft.bconverter.media.api.MediaEngine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the converter flow (spec §17): pick → probe → configure → destination → convert → saved.
 * The staged input is owned here until the conversion is saved, the user starts over, or the screen is left,
 * so a failed or cancelled conversion can be retried without copying the file again.
 */
@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val prober: MediaProber,
    private val capabilities: CapabilitySource,
    engine: MediaEngine,
    private val resolver: InputResolver,
    private val staging: StagingDirectory,
    private val publisher: OutputPublisher,
) : ViewModel() {
    private val session = ConversionSession(engine, resolver)
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()
    private val _events = Channel<ConverterEvent>(Channel.BUFFERED)
    val events: Flow<ConverterEvent> = _events.receiveAsFlow()

    private var input: ResolvedInput? = null
    private var repository: MediaCapabilityRepository? = null
    private var conversionJob: Job? = null

    init {
        viewModelScope.launch { session.state.collect { s -> _state.update { it.copy(processing = s) } } }
    }

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
                    source = SourceInfo(resolved.displayName, resolved.sizeBytes, outcome.metadata),
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
        val settings = ConversionPresetFactory(repo).create(form.preset, metadata, form.container, form.videoCodec, form.audioCodec)
        _state.update {
            it.copy(
                form = form, settings = settings,
                videoCodecs = repo.getVideoCodecs(form.container), audioCodecs = repo.getAudioCodecs(form.container),
                issues = emptyList(),
            )
        }
    }

    fun onPresetSelected(preset: ConversionPreset) {
        _state.value.form?.let { applyForm(it.copy(preset = preset)) }
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
        val issues = ConversionRequestValidator(repo).validate(settings, metadata, form.outputName)
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
        conversionJob = viewModelScope.launch {
            session.run(request, releaseInput = false)
            when (session.state.value) {
                ProcessingState.Completed -> save(temp, destination)
                is ProcessingState.Failed -> backToForm(ConverterMessage.ConversionFailed)
                else -> backToForm(null) // cancelled
            }
            session.reset()
        }
    }

    private suspend fun save(temp: File, destination: Uri) {
        val result = publisher.publish(temp, destination)
        temp.delete()
        when (result) {
            PublishResult.Success -> { releaseInput(); _state.update { it.copy(stage = ConverterStage.Completed) } }
            PublishResult.Failure -> backToForm(ConverterMessage.SaveFailed)
        }
    }

    private fun backToForm(message: ConverterMessage?) {
        _state.update { it.copy(stage = ConverterStage.Configure, message = message) }
    }

    fun onCancel() {
        if (_state.value.stage != ConverterStage.Converting) return
        viewModelScope.launch { session.cancel() }
    }

    fun onConvertAnother() {
        conversionJob?.cancel()
        releaseInput()
        repository = null
        _state.value = ConverterUiState()
    }

    private fun releaseInput() {
        input?.let { resolver.release(it) }
        input = null
    }

    override fun onCleared() { releaseInput() }
}
