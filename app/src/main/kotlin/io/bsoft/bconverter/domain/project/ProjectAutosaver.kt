package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.VideoProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AutosaveResult {
    data class Saved(val stored: StoredProject) : AutosaveResult
    /** Nothing changed since the last successful save, so nothing was written. */
    data object Unchanged : AutosaveResult
    /** The write failed; the previous version is intact and the next [ProjectAutosaver.save] retries the change. */
    data class Failed(val message: String) : AutosaveResult
}

/**
 * Saves a project after important edits (spec §41). Only successful writes count: after a failure the same project
 * is written again on the next call. Creation time is kept; modification time is stamped by [time].
 */
class ProjectAutosaver(private val repository: ProjectRepository, private val time: TimeSource) {
    private val lock = Mutex()
    private var lastSaved: StoredProject? = null

    /** Continue an existing project (e.g. one just opened) so its creation time is kept and unchanged saves are skipped. */
    suspend fun resume(stored: StoredProject) = lock.withLock { lastSaved = stored.copy(project = stored.project.copy(sourceMetadata = emptyMap())) }

    suspend fun save(project: VideoProject): AutosaveResult = lock.withLock {
        val persistable = project.copy(sourceMetadata = emptyMap())
        val previous = lastSaved
        if (previous != null && previous.project == persistable) return AutosaveResult.Unchanged
        val now = time.nowMs()
        val stored = StoredProject(persistable, createdAtMs = previous?.createdAtMs ?: now, modifiedAtMs = now)
        try {
            repository.save(stored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AutosaveResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        lastSaved = stored
        AutosaveResult.Saved(stored)
    }
}
