package io.bsoft.bconverter.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProjectDao {
    /** A single statement, so it is atomic: an interrupted save leaves the previous row untouched. */
    @Upsert suspend fun upsert(entity: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: String): ProjectEntity?

    @Query("SELECT id, name, modified_at_ms, clip_count, duration_ms FROM projects ORDER BY modified_at_ms DESC")
    suspend fun summaries(): List<ProjectSummaryRow>

    /** Every saved project's serialized text, for finding files no project refers to. */
    @Query("SELECT payload FROM projects") suspend fun payloads(): List<String>

    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: String)
}
