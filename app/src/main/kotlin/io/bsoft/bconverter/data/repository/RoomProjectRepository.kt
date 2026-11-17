package io.bsoft.bconverter.data.repository

import io.bsoft.bconverter.data.database.ProjectDao
import io.bsoft.bconverter.data.database.ProjectEntity
import io.bsoft.bconverter.domain.project.DecodeResult
import io.bsoft.bconverter.domain.project.ProjectLoadResult
import io.bsoft.bconverter.domain.project.ProjectRepository
import io.bsoft.bconverter.domain.project.ProjectSerializer
import io.bsoft.bconverter.domain.project.ProjectSummary
import io.bsoft.bconverter.domain.project.StoredProject

class RoomProjectRepository(private val dao: ProjectDao) : ProjectRepository {
    override suspend fun save(stored: StoredProject) {
        val p = stored.project
        dao.upsert(
            ProjectEntity(
                id = p.id, name = p.name, createdAtMs = stored.createdAtMs, modifiedAtMs = stored.modifiedAtMs,
                clipCount = p.timeline.clipCount, durationMs = p.timeline.durationMs,
                payload = ProjectSerializer.encode(stored),
            ),
        )
    }

    override suspend fun load(id: String): ProjectLoadResult {
        val row = dao.get(id) ?: return ProjectLoadResult.NotFound
        return when (val decoded = ProjectSerializer.decode(row.payload)) {
            is DecodeResult.Decoded -> ProjectLoadResult.Loaded(decoded.stored)
            is DecodeResult.Corrupt -> ProjectLoadResult.Corrupt
        }
    }

    override suspend fun list(): List<ProjectSummary> =
        dao.summaries().map { ProjectSummary(it.id, it.name, it.modifiedAtMs, it.clipCount, it.durationMs) }

    override suspend fun delete(id: String) = dao.delete(id)
}
