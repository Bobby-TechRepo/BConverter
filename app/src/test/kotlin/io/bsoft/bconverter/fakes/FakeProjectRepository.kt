package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.project.ProjectLoadResult
import io.bsoft.bconverter.domain.project.ProjectRepository
import io.bsoft.bconverter.domain.project.ProjectSummary
import io.bsoft.bconverter.domain.project.StoredProject

/** In-memory repository. [failNextSave] simulates an interrupted save: nothing is written. */
class FakeProjectRepository : ProjectRepository {
    private val items = LinkedHashMap<String, StoredProject>()
    private val corrupt = HashSet<String>()
    var saveCount = 0; private set
    var failNextSave = false
    var failList = false
    var failDelete = false

    override suspend fun save(stored: StoredProject) {
        if (failNextSave) { failNextSave = false; throw java.io.IOException("disk full") }
        saveCount++
        items[stored.project.id] = stored
        corrupt.remove(stored.project.id)
    }

    override suspend fun load(id: String): ProjectLoadResult = when {
        id in corrupt -> ProjectLoadResult.Corrupt
        else -> items[id]?.let { ProjectLoadResult.Loaded(it) } ?: ProjectLoadResult.NotFound
    }

    override suspend fun list(): List<ProjectSummary> {
        if (failList) throw java.io.IOException("db unavailable")
        return items.values.sortedByDescending { it.modifiedAtMs }.map { ProjectSummary.of(it) }
    }

    override suspend fun delete(id: String) {
        if (failDelete) throw java.io.IOException("db unavailable")
        items.remove(id); corrupt.remove(id)
    }

    /** Makes [id] load as corrupt (it still appears in the list, as a damaged row would). */
    fun corrupt(id: String) { corrupt += id }
}
