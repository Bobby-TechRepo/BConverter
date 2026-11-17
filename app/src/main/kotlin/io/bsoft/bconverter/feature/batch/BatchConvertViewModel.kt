package io.bsoft.bconverter.feature.batch

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.core.storage.ContentReader
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.UriPermissionPersister
import io.bsoft.bconverter.domain.usecase.BatchConverter
import io.bsoft.bconverter.domain.usecase.BatchOptions
import io.bsoft.bconverter.domain.usecase.BatchRun
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file picked for the batch, with what the picker knows about it (null when the provider does not say). */
data class PickedFile(val uri: Uri, val name: String? = null, val sizeBytes: Long? = null)

data class BatchUiState(
    val picked: List<PickedFile> = emptyList(),
    val options: BatchOptions = BatchOptions(),
    /** Formats this device can write; empty until known. */
    val containers: List<ContainerFormat> = emptyList(),
    /** The batch running now or just finished; while set, the screen shows it instead of the list being picked. */
    val run: BatchRun? = null,
) {
    val canStart: Boolean get() = run == null && picked.isNotEmpty() && options.container in containers
}

/**
 * Picking several videos, choosing one quality and one format for all of them, and following the batch
 * ([BatchConverter], step 25). The picks and choices survive the process being killed; the batch itself lives on
 * an app-wide scope, so this screen can be left and reopened while it runs.
 */
@HiltViewModel
class BatchConvertViewModel @Inject constructor(
    private val batch: BatchConverter,
    private val capabilities: CapabilitySource,
    private val reader: ContentReader,
    private val grants: UriPermissionPersister,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val form = MutableStateFlow(
        BatchUiState(
            picked = saved.get<ArrayList<Uri>>(KEY_URIS).orEmpty().map { PickedFile(it) },
            options = BatchOptions(
                saved.get<String>(KEY_PRESET)?.let { runCatching { ConversionPreset.valueOf(it) }.getOrNull() } ?: ConversionPreset.BALANCED,
                saved.get<String>(KEY_CONTAINER)?.let { runCatching { ContainerFormat.valueOf(it) }.getOrNull() } ?: ContainerFormat.MP4,
            ),
        ),
    )

    val state: StateFlow<BatchUiState> = combine(form, batch.run) { f, run -> f.copy(run = run) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, form.value.copy(run = batch.run.value))

    init {
        viewModelScope.launch {
            val containers = capabilities.repository().getContainers()
            form.update { f ->
                val container = when {
                    f.options.container in containers -> f.options.container
                    ContainerFormat.MP4 in containers -> ContainerFormat.MP4
                    else -> containers.firstOrNull() ?: f.options.container
                }
                f.copy(containers = containers, options = f.options.copy(container = container))
            }
            saveOptions()
        }
        describe(form.value.picked.map { it.uri })
    }

    /** Adds the picked files to the list (each once, in the order picked); an empty pick changes nothing. */
    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty() || state.value.run?.running == true) return
        // A finished batch is put away when new files are picked.
        if (state.value.run != null) batch.clear()
        val known = form.value.picked.map { it.uri }.toSet()
        val added = uris.distinct().filter { it !in known }.take((MAX_FILES - known.size).coerceAtLeast(0))
        if (added.isEmpty()) return
        // Best effort: keeps reading access if the batch outlives this screen (released at the next start if unused).
        added.forEach(grants::persist)
        form.update { it.copy(picked = it.picked + added.map { u -> PickedFile(u) }) }
        savePicks()
        describe(added)
    }

    fun onRemove(uri: Uri) {
        form.update { it.copy(picked = it.picked.filterNot { p -> p.uri == uri }) }
        savePicks()
    }

    fun onPreset(preset: ConversionPreset) { form.update { it.copy(options = it.options.copy(preset = preset)) }; saveOptions() }

    fun onContainer(container: ContainerFormat) {
        if (container !in form.value.containers) return
        form.update { it.copy(options = it.options.copy(container = container)) }
        saveOptions()
    }

    /** [folder] is null when the folder picker was dismissed. The list is handed to the batch and cleared here. */
    fun onFolderChosen(folder: Uri?) {
        if (folder == null || !state.value.canStart) return
        val f = form.value
        if (batch.start(f.picked.map { it.uri }, f.options, folder)) {
            form.update { it.copy(picked = emptyList()) }
            savePicks()
        }
    }

    fun onCancel() = batch.cancel()

    /** After a batch: back to an empty list. */
    fun onConvertMore() = batch.clear()

    /** Fills in names and sizes for the list (off the main thread; a file the provider cannot describe keeps its Uri). */
    private fun describe(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val infos = withContext(io) { uris.associateWith { u -> runCatching { reader.info(u) }.getOrNull() } }
            form.update { f ->
                f.copy(picked = f.picked.map { p -> infos[p.uri]?.let { i -> p.copy(name = i.displayName ?: p.name, sizeBytes = i.sizeBytes ?: p.sizeBytes) } ?: p })
            }
        }
    }

    private fun savePicks() { saved[KEY_URIS] = ArrayList(form.value.picked.map { it.uri }) }

    private fun saveOptions() {
        saved[KEY_PRESET] = form.value.options.preset.name
        saved[KEY_CONTAINER] = form.value.options.container.name
    }

    companion object {
        /** More than this many in one batch is cut to it (the Photo Picker allows the same). */
        const val MAX_FILES = 50
        private const val KEY_URIS = "batch.uris"
        private const val KEY_PRESET = "batch.preset"
        private const val KEY_CONTAINER = "batch.container"
    }
}
