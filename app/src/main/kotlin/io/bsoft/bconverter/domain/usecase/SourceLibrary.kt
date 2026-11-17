package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.model.ProbeOutcome
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject

/**
 * The media a project uses. Clips refer to the original `content://` URIs (so a project can be saved and reopened);
 * each picked file is also staged as a plain file and probed here, for preview and rendering. A file used by several
 * clips is staged once.
 */
class SourceLibrary(private val prober: MediaProber, private val resolver: InputResolver) {
    private class Entry(val input: ResolvedInput, val metadata: MediaMetadata)

    sealed interface AddResult {
        data class Added(
            val source: MediaSource.UriSource,
            val metadata: MediaMetadata,
            val displayName: String,
            /** False when the file was already in the library (nothing new to clean up if the caller rejects it). */
            val newlyStaged: Boolean,
        ) : AddResult
        data class Failed(val failure: ProbeFailure) : AddResult
    }

    private val entries = LinkedHashMap<MediaSource, Entry>()

    suspend fun add(uri: Uri): AddResult {
        val source = MediaSource.UriSource(uri)
        entries[source]?.let { return AddResult.Added(source, it.metadata, it.input.displayName, newlyStaged = false) }
        return when (val outcome = prober.probe(source)) {
            is ProbeOutcome.Failure -> AddResult.Failed(outcome.failure)
            is ProbeOutcome.Success -> {
                entries[source] = Entry(outcome.input, outcome.metadata)
                AddResult.Added(source, outcome.metadata, outcome.input.displayName, newlyStaged = true)
            }
        }
    }

    fun metadata(source: MediaSource): MediaMetadata? = entries[source]?.metadata

    fun inputs(): List<ResolvedInput> = entries.values.map { it.input }

    fun discard(source: MediaSource) {
        entries.remove(source)?.let { resolver.release(it.input) }
    }

    /**
     * A copy of [project] whose sources are the staged files, with probe results keyed the same way, ready for the
     * render planner. Sources that are not staged (e.g. after reopening a project) are left unchanged so the planner
     * can report them.
     */
    fun forRender(project: VideoProject): VideoProject {
        val staged = entries.mapValues { (_, entry) -> MediaSource.FileSource(entry.input.file) as MediaSource }
        return project.copy(
            timeline = project.timeline.mapSources { staged[it] ?: it },
            sourceMetadata = entries.values.associate { MediaSource.FileSource(it.input.file) as MediaSource to it.metadata },
        )
    }

    /** [timeline] with every staged source replaced by its file, for preview; sources that are not staged are left unchanged. */
    fun staged(timeline: Timeline): Timeline {
        val files = entries.mapValues { (_, entry) -> MediaSource.FileSource(entry.input.file) as MediaSource }
        return timeline.mapSources { files[it] ?: it }
    }

    fun releaseAll() {
        entries.values.forEach { resolver.release(it.input) }
        entries.clear()
    }

    /** Releases everything except [keep] (copies whose ownership moved elsewhere, e.g. to a running export). */
    fun releaseAllExcept(keep: List<ResolvedInput>) {
        entries.values.filter { it.input !in keep }.forEach { resolver.release(it.input) }
        entries.clear()
    }
}
