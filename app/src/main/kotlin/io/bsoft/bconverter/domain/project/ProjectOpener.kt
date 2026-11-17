package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.Timeline

fun interface SourceAvailability { fun isAvailable(source: MediaSource): Boolean }

/** A source that can no longer be read, and the clips that depend on it. */
data class MissingSource(val source: MediaSource, val clipIds: List<String>)

class MissingMediaChecker(private val availability: SourceAvailability) {
    /** Each unreadable source once, in order of first use, with every clip (video first, then audio, then picture-in-picture) that uses it. */
    fun check(timeline: Timeline): List<MissingSource> {
        val users = LinkedHashMap<MediaSource, MutableList<String>>()
        timeline.videoClips.forEach { users.getOrPut(it.source) { mutableListOf() } += it.id }
        timeline.audioClips.forEach { users.getOrPut(it.source) { mutableListOf() } += it.id }
        timeline.pipClips.forEach { users.getOrPut(it.source) { mutableListOf() } += it.id }
        return users.filterKeys { !availability.isAvailable(it) }.map { (s, ids) -> MissingSource(s, ids) }
    }
}

sealed interface OpenResult {
    data class Opened(val stored: StoredProject, val missing: List<MissingSource>) : OpenResult
    data object NotFound : OpenResult
    data object Corrupt : OpenResult
}

/** Loads a saved project and reports media that has gone missing; a project with missing media still opens. */
class ProjectOpener(private val repository: ProjectRepository, availability: SourceAvailability) {
    private val checker = MissingMediaChecker(availability)

    suspend fun open(id: String): OpenResult = when (val r = repository.load(id)) {
        is ProjectLoadResult.Loaded -> OpenResult.Opened(r.stored, checker.check(r.stored.project.timeline))
        ProjectLoadResult.NotFound -> OpenResult.NotFound
        ProjectLoadResult.Corrupt -> OpenResult.Corrupt
    }
}
