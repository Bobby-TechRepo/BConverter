package io.bsoft.bconverter.feature.subtitleextractor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.repository.DocumentCreator
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.StorageCheck
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.domain.usecase.SubtitleBatchPlanner
import io.bsoft.bconverter.domain.usecase.SubtitleExtractionPlanner
import io.bsoft.bconverter.domain.usecase.SubtitleExtractionValidator
import io.bsoft.bconverter.domain.usecase.OutputNames
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.SourceInfo
import io.bsoft.bconverter.media.api.SubtitleExtractionRequest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Subtitle extraction: pick → probe → choose tracks and formats → choose a folder → one job per track → done.
 * Jobs run one after another; a failed track does not stop the others.
 */
@HiltViewModel
class SubtitleExtractorViewModel @Inject constructor(
    private val prober: MediaProber,
    private val resolver: InputResolver,
    private val staging: StagingDirectory,
    private val storage: StorageGuard,
    private val jobs: ExportJobLauncher,
    private val documents: DocumentCreator,
) : ViewModel() {
    private val _state = MutableStateFlow(SubtitleExtractorUiState())
    val state: StateFlow<SubtitleExtractorUiState> = _state.asStateFlow()

    private val planner = SubtitleExtractionPlanner()
    private val batch = SubtitleBatchPlanner(planner)
    private var input: ResolvedInput? = null
    private var runJob: Job? = null
    private var activeJobId: String? = null

    fun onInputPicked(uri: Uri?) {
        if (uri == null) return
        val stage = _state.value.stage
        if (stage == ConverterStage.Probing || stage == ConverterStage.Extracting) return
        releaseInput()
        _state.value = SubtitleExtractorUiState(stage = ConverterStage.Probing)
        viewModelScope.launch { open(uri) }
    }

    private suspend fun open(uri: Uri) {
        when (val outcome = prober.probe(MediaSource.UriSource(uri))) {
            is ProbeOutcome.Failure -> _state.value = SubtitleExtractorUiState(message = when (val f = outcome.failure) {
                is ProbeFailure.Input -> ConverterMessage.InputProblem(f.error)
                else -> ConverterMessage.ProbeFailed
            })
            is ProbeOutcome.Success -> {
                val resolved = outcome.input
                if (outcome.metadata.subtitleStreams.isEmpty()) {
                    resolver.release(resolved)
                    _state.value = SubtitleExtractorUiState(message = ConverterMessage.NoSubtitleStream)
                    return
                }
                input = resolved
                // Text tracks start selected in their natural format; image tracks are opt-in.
                val initial = outcome.metadata.subtitleStreams.withIndex()
                    .filter { it.value.kind == io.bsoft.bconverter.domain.model.SubtitleKind.TEXT }
                    .mapNotNull { (i, s) -> planner.defaultOutput(s)?.let { i to it } }.toMap()
                _state.value = SubtitleExtractorUiState(
                    stage = ConverterStage.Configure,
                    source = SourceInfo(resolved.displayName, resolved.sizeBytes, outcome.metadata, resolved.file),
                    selection = initial,
                    baseName = resolved.displayName.substringBeforeLast('.', resolved.displayName).ifBlank { "subtitles" },
                )
            }
        }
    }

    fun onTrackToggled(ordinal: Int, selected: Boolean) {
        val stream = _state.value.source?.metadata?.subtitleStreams?.getOrNull(ordinal) ?: return
        _state.update { s ->
            val next = if (selected) {
                val output = planner.defaultOutput(stream) ?: return@update s
                s.selection + (ordinal to output)
            } else s.selection - ordinal
            s.copy(selection = next, issues = emptyList())
        }
    }

    fun onOutputSelected(ordinal: Int, output: SubtitleOutput) {
        val stream = _state.value.source?.metadata?.subtitleStreams?.getOrNull(ordinal) ?: return
        if (output !in planner.outputsFor(stream)) return
        _state.update { it.copy(selection = it.selection + (ordinal to output), issues = emptyList()) }
    }

    fun onBaseNameChanged(name: String) = _state.update { it.copy(baseName = name, issues = emptyList()) }

    /** Validates; returns true when the screen should now ask for a folder. */
    fun onStartRequested(): Boolean {
        val s = _state.value
        val metadata = s.source?.metadata ?: return false
        if (s.stage != ConverterStage.Configure) return false
        val validator = SubtitleExtractionValidator(planner)
        val issues = buildList {
            if (s.selection.isEmpty()) add(ConversionIssue.InvalidSubtitleStream)
            s.selection.forEach { (ordinal, output) ->
                addAll(validator.validate(io.bsoft.bconverter.domain.model.SubtitleExtractionSettings(ordinal, output), metadata, s.baseName))
            }
            if (!OutputNames.isValid(s.baseName)) add(ConversionIssue.InvalidOutputName)
            // Subtitle files are tiny; require a modest amount of free space for the temporary copy.
            (storage.check(MIN_FREE_BYTES) as? StorageCheck.Insufficient)
                ?.let { add(ConversionIssue.InsufficientStorage(it.requiredBytes, it.availableBytes)) }
        }.distinct()
        _state.update { it.copy(issues = issues, message = null) }
        return issues.isEmpty()
    }

    /** [folder] is null when the user dismissed the folder picker. */
    fun onFolderChosen(folder: Uri?) {
        if (folder == null) return
        val s = _state.value
        val source = s.source ?: return
        val resolved = input ?: return
        if (s.stage != ConverterStage.Configure) return
        val plan = batch.plan(s.baseName, source.metadata, s.selection)
        if (plan.isEmpty()) return
        _state.update { it.copy(stage = ConverterStage.Extracting, totalFiles = plan.size, currentFile = 0, progress = 0f, savedCount = 0, failedCount = 0, message = null) }
        runJob = viewModelScope.launch {
            var saved: SavedOutput? = null
            var failed = 0
            var lastFailure: ConverterMessage? = null
            plan.forEachIndexed { i, item ->
                _state.update { it.copy(currentFile = i, progress = 0f) }
                val output = item.settings.output
                val destination = documents.create(folder, item.fileName, output.mimeType)
                if (destination == null) { failed++; lastFailure = ConverterMessage.SaveFailed; return@forEachIndexed }
                val jobId = UUID.randomUUID().toString()
                val temp = staging.newFile("$jobId.${output.extension}")
                val request = SubtitleExtractionRequest(jobId, resolved, source.metadata, temp, item.settings)
                activeJobId = jobId
                val terminal = jobs.launch(ExportJob(jobId, source.displayName, ExportTask.ExtractSubtitle(request), destination))
                    .onEach { st -> if (st is JobStatus.Running) _state.update { it.copy(progress = st.progress.coerceIn(0f, 1f)) } }
                    .first { it.isTerminal }
                jobs.finish(jobId)
                activeJobId = null
                when (terminal) {
                    JobStatus.Completed -> saved = SavedOutput(destination, output.mimeType)
                    JobStatus.Cancelled -> { _state.update { it.copy(stage = ConverterStage.Configure, message = null) }; return@launch }
                    else -> { failed++; lastFailure = ConverterMessage.ConversionFailed }
                }
            }
            val savedCount = plan.size - failed
            if (savedCount == 0) {
                _state.update { it.copy(stage = ConverterStage.Configure, message = lastFailure) }
            } else {
                releaseInput()
                _state.update { it.copy(stage = ConverterStage.Completed, saved = saved, savedCount = savedCount, failedCount = failed) }
            }
        }
    }

    fun onCancel() {
        if (_state.value.stage != ConverterStage.Extracting) return
        activeJobId?.let { jobs.cancel(it) }
    }

    fun onExtractAnother() {
        runJob?.cancel()
        releaseInput()
        _state.value = SubtitleExtractorUiState()
    }

    private fun releaseInput() {
        input?.let { resolver.release(it) }
        input = null
    }

    override fun onCleared() {
        val running = activeJobId
        runJob?.cancel()
        if (running != null) jobs.detach(running) else releaseInput()
    }

    private companion object { const val MIN_FREE_BYTES = 8L * 1024 * 1024 }
}
