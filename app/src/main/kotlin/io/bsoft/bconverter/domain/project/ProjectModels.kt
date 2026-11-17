package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.VideoProject

/** A project as persisted (spec §40): the project itself plus bookkeeping. Probe results are not stored; sources are probed again. */
data class StoredProject(val project: VideoProject, val createdAtMs: Long, val modifiedAtMs: Long)

/** What the "Recent Projects" list needs, readable without decoding the whole project. */
data class ProjectSummary(val id: String, val name: String, val modifiedAtMs: Long, val clipCount: Int, val durationMs: Long) {
    companion object {
        fun of(stored: StoredProject) = ProjectSummary(
            stored.project.id, stored.project.name, stored.modifiedAtMs,
            stored.project.timeline.clipCount, stored.project.timeline.durationMs,
        )
    }
}

sealed interface ProjectLoadResult {
    data class Loaded(val stored: StoredProject) : ProjectLoadResult
    data object NotFound : ProjectLoadResult
    /** The stored data exists but cannot be read. */
    data object Corrupt : ProjectLoadResult
}

/** Project storage. [save] must be atomic: if it throws, the previously saved version is still intact. */
interface ProjectRepository {
    suspend fun save(stored: StoredProject)
    suspend fun load(id: String): ProjectLoadResult
    /** Most recently modified first. */
    suspend fun list(): List<ProjectSummary>
    suspend fun delete(id: String)
}

fun interface TimeSource { fun nowMs(): Long }
