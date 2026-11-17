package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ExportJob
import io.bsoft.bconverter.domain.model.ExportTask
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.DocumentCreator
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.api.ConversionRequest
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The choices that apply to every file of a batch; codecs are chosen automatically per file. */
data class BatchOptions(val preset: ConversionPreset = ConversionPreset.BALANCED, val container: ContainerFormat = ContainerFormat.MP4)

/** Why a file of a batch was not converted before anything was run for it. */
enum class BatchSkip {
    /** The file could not be opened or read (gone, no permission, not media). */
    UNREADABLE,
    /** No picture to convert (an audio file, say). */
    NO_VIDEO,
    /** These settings cannot produce this file (no encoder for its streams in the chosen format). */
    NOT_CONVERTIBLE,
    /** Not enough free space for its temporary copy. */
    NO_SPACE,
    /** The output file could not be created in the chosen folder. */
    CANNOT_SAVE,
}

sealed interface BatchItemStatus {
    data object Waiting : BatchItemStatus
    data object Preparing : BatchItemStatus
    data class Converting(val progress: Float) : BatchItemStatus
    data class Saved(val output: SavedOutput, val fileName: String) : BatchItemStatus
    data class Skipped(val reason: BatchSkip) : BatchItemStatus
    data class Failed(val reason: JobFailure) : BatchItemStatus
    data object Cancelled : BatchItemStatus

    val isFinal: Boolean get() = this is Saved || this is Skipped || this is Failed || this is Cancelled
}

/** One file of a batch; [name] is the picked file's name once it has been opened (null until then). */
data class BatchItem(val uri: Uri, val status: BatchItemStatus = BatchItemStatus.Waiting, val name: String? = null)

/** A batch being converted, or the result of the last one. */
data class BatchRun(val items: List<BatchItem>, val options: BatchOptions, val running: Boolean) {
    val savedCount: Int get() = items.count { it.status is BatchItemStatus.Saved }
    val problemCount: Int get() = items.count { it.status is BatchItemStatus.Skipped || it.status is BatchItemStatus.Failed }
    /** The file being worked on (0-based), or null when none is. */
    val currentIndex: Int? get() = items.indexOfFirst { it.status == BatchItemStatus.Preparing || it.status is BatchItemStatus.Converting }.takeIf { it >= 0 }
    /** Share of the whole batch done: finished files count whole, the one converting counts by its progress. */
    val progress: Float get() = if (items.isEmpty()) 0f else items.sumOf { i ->
        when (val s = i.status) {
            is BatchItemStatus.Converting -> s.progress.toDouble()
            else -> if (s.isFinal) 1.0 else 0.0
        }
    }.toFloat() / items.size
}

/** Unique output names inside one batch: "<name>-converted.<ext>", then "<name>-converted (2).<ext>", … */
object BatchNames {
    private const val SUFFIX = "-converted"
    private const val MAX_BASE = 120
    private const val FORBIDDEN = "/\\:*?\"<>|"

    fun fileName(displayName: String, container: ContainerFormat, used: MutableSet<String>): String {
        val base = baseOf(displayName)
        var candidate = "$base$SUFFIX.${container.extension}"
        var n = 2
        while (!used.add(candidate.lowercase())) candidate = "$base$SUFFIX ($n).${container.extension}".also { n++ }
        return candidate
    }

    /** The name without its extension, made safe for a file name (forbidden characters become "_"), never blank. */
    fun baseOf(displayName: String): String {
        val withoutExtension = displayName.substringBeforeLast('.', displayName)
        val cleaned = withoutExtension.map { if (it in FORBIDDEN || it.isISOControl()) '_' else it }.joinToString("").trim().trim('.')
        return cleaned.take(MAX_BASE).ifBlank { "video" }
    }
}

/**
 * Converts several files with the same settings into one folder (implementation plan step 25). Files are opened and
 * converted one after another, each as an ordinary background export job (so it shows in Jobs and keeps its own
 * notification); only one file's input is open at a time. A file that cannot be converted is skipped with a reason and the
 * rest carry on. The batch lives on an app-wide scope, so leaving the screen does not stop it; it cannot survive the app
 * process being killed (the job in progress is then reported as interrupted, the rest are never started).
 */
class BatchConverter(
    private val prober: MediaProber,
    private val capabilities: CapabilitySource,
    private val resolver: InputResolver,
    private val staging: StagingDirectory,
    private val storage: StorageGuard,
    private val jobs: ExportJobLauncher,
    private val documents: DocumentCreator,
    private val scope: CoroutineScope,
) {
    private val _run = MutableStateFlow<BatchRun?>(null)
    /** The batch running now, or the last one until [clear]; null when there is none. */
    val run: StateFlow<BatchRun?> = _run.asStateFlow()

    private var loop: Job? = null
    @Volatile private var activeJobId: String? = null
    @Volatile private var cancelRequested = false

    /** Starts converting [uris] into [folder]; false (nothing started) while another batch is running or [uris] is empty. */
    fun start(uris: List<Uri>, options: BatchOptions, folder: Uri): Boolean {
        if (_run.value?.running == true || uris.isEmpty()) return false
        cancelRequested = false
        _run.value = BatchRun(uris.distinct().map { BatchItem(it) }, options, running = true)
        loop = scope.launch {
            val used = mutableSetOf<String>()
            try {
                for (index in _run.value!!.items.indices) {
                    if (cancelRequested) break
                    if (!convertOne(index, options, folder, used)) break
                }
            } finally {
                // Whatever was not reached is cancelled; the batch is over.
                _run.update { r -> r?.copy(running = false, items = r.items.map { if (it.status.isFinal) it else it.copy(status = BatchItemStatus.Cancelled) }) }
            }
        }
        return true
    }

    /** Stops the file in progress and does not start the rest. Files already saved stay saved. */
    fun cancel() {
        if (_run.value?.running != true) return
        cancelRequested = true
        val active = activeJobId
        if (active != null) jobs.cancel(active) else loop?.cancel()
    }

    /** Forgets a finished batch. */
    fun clear() { if (_run.value?.running != true) _run.value = null }

    /** Converts item [index]; returns false when the batch must stop (cancelled). */
    private suspend fun convertOne(index: Int, options: BatchOptions, folder: Uri, used: MutableSet<String>): Boolean {
        set(index) { it.copy(status = BatchItemStatus.Preparing) }
        val uri = _run.value!!.items[index].uri
        val outcome = prober.probe(MediaSource.UriSource(uri))
        if (outcome !is ProbeOutcome.Success) { skip(index, BatchSkip.UNREADABLE); return true } // the prober released it
        val input = outcome.input
        set(index) { it.copy(name = input.displayName) }
        // Set when a running job is left to finish (and release the input) by itself.
        var handedOver = false
        try {
            val metadata = outcome.metadata
            if (!metadata.hasVideo) { skip(index, BatchSkip.NO_VIDEO); return true }
            val repo = capabilities.repository()
            if (options.container !in repo.getContainers()) { skip(index, BatchSkip.NOT_CONVERTIBLE); return true }
            val settings = ConversionPresetFactory(repo).create(options.preset, metadata, options.container)
            if (settings == null) { skip(index, BatchSkip.NOT_CONVERTIBLE); return true }
            val fileName = BatchNames.fileName(input.displayName, options.container, used)
            if (ConversionRequestValidator(repo).validate(settings, metadata, fileName.substringBeforeLast('.')).isNotEmpty()) {
                skip(index, BatchSkip.NOT_CONVERTIBLE); return true
            }
            val estimate = StorageEstimator.estimateConversion(settings, metadata, input.sizeBytes)
            if (storage.check(estimate) is StorageCheck.Insufficient) { skip(index, BatchSkip.NO_SPACE); return true }
            if (cancelRequested) return false
            val destination = documents.create(folder, fileName, options.container.mimeType)
            if (destination == null) { skip(index, BatchSkip.CANNOT_SAVE); return true }

            val jobId = UUID.randomUUID().toString()
            val request = ConversionRequest(jobId, input, metadata, staging.newFile("$jobId.${options.container.extension}"), settings)
            set(index) { it.copy(status = BatchItemStatus.Converting(0f)) }
            activeJobId = jobId
            // A cancel that arrived between the check above and the launch still stops this file.
            val status = jobs.launch(ExportJob(jobId, input.displayName, ExportTask.Convert(request), destination))
            if (cancelRequested) jobs.cancel(jobId)
            val terminal = status
                .onEach { s -> if (s is JobStatus.Running) set(index) { it.copy(status = BatchItemStatus.Converting(s.progress.coerceIn(0f, 1f))) } }
                .first { it.isTerminal }
            jobs.finish(jobId)
            activeJobId = null
            when (terminal) {
                JobStatus.Completed -> set(index) { it.copy(status = BatchItemStatus.Saved(SavedOutput(destination, options.container.mimeType), fileName)) }
                is JobStatus.Failed -> set(index) { it.copy(status = BatchItemStatus.Failed(terminal.reason)) }
                else -> { set(index) { it.copy(status = BatchItemStatus.Cancelled) }; return false }
            }
            return !cancelRequested
        } catch (e: CancellationException) {
            // Stopped while a job was being handed over: stop it too, and let it release the input when it ends.
            activeJobId?.let { jobs.detach(it); jobs.cancel(it); handedOver = true }
            set(index) { it.copy(status = BatchItemStatus.Cancelled) }
            throw e
        } finally {
            activeJobId = null
            if (!handedOver) release(input)
        }
    }

    private fun release(input: ResolvedInput) = resolver.release(input)

    private fun skip(index: Int, reason: BatchSkip) = set(index) { it.copy(status = BatchItemStatus.Skipped(reason)) }

    private fun set(index: Int, change: (BatchItem) -> BatchItem) {
        _run.update { r -> r?.copy(items = r.items.mapIndexed { i, item -> if (i == index) change(item) else item }) }
    }
}
