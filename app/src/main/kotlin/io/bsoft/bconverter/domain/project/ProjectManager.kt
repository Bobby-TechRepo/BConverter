package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.usecase.OutputNames
import kotlinx.coroutines.CancellationException

enum class ProjectActionResult { Success, NotFound, Corrupt, InvalidName, Failed }

object ProjectNaming {
    private const val MAX_LENGTH = 200
    private const val SUFFIX = " copy"
    private val COPY_SUFFIX = Regex(" copy( \\d+)?$")

    /** "Holiday" → "Holiday copy" → "Holiday copy 2" …; copying a copy continues the numbering instead of stacking suffixes. */
    fun copyName(name: String, existing: Set<String>): String {
        val base = name.trim().replace(COPY_SUFFIX, "").take(MAX_LENGTH - SUFFIX.length - 4).trimEnd()
        var candidate = base + SUFFIX
        var n = 2
        while (candidate in existing) { candidate = "$base$SUFFIX $n"; n++ }
        return candidate
    }
}

/** File management for saved projects: rename and duplicate (spec Phase 16). Failures are results, never exceptions. */
class ProjectManager(
    private val repository: ProjectRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    suspend fun rename(id: String, newName: String): ProjectActionResult {
        if (!OutputNames.isValid(newName)) return ProjectActionResult.InvalidName
        return withLoaded(id) { stored ->
            repository.save(stored.copy(project = stored.project.copy(name = newName.trim()), modifiedAtMs = time.nowMs()))
        }
    }

    suspend fun duplicate(id: String): ProjectActionResult = withLoaded(id) { stored ->
        val existing = repository.list().map { it.name }.toSet()
        val now = time.nowMs()
        repository.save(
            StoredProject(
                stored.project.copy(id = ids.next(), name = ProjectNaming.copyName(stored.project.name, existing)),
                createdAtMs = now, modifiedAtMs = now,
            ),
        )
    }

    private suspend fun withLoaded(id: String, action: suspend (StoredProject) -> Unit): ProjectActionResult {
        val stored = try {
            when (val loaded = repository.load(id)) {
                is ProjectLoadResult.Loaded -> loaded.stored
                ProjectLoadResult.NotFound -> return ProjectActionResult.NotFound
                ProjectLoadResult.Corrupt -> return ProjectActionResult.Corrupt
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return ProjectActionResult.Failed
        }
        return try {
            action(stored); ProjectActionResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ProjectActionResult.Failed
        }
    }
}
