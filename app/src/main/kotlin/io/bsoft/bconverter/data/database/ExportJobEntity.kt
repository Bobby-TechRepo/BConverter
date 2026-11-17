package io.bsoft.bconverter.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One row per export job (step 17). [kind] and [outcome] hold enum names; unknown names read as defaults. */
@Entity(tableName = "export_jobs")
data class ExportJobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val destination: String,
    val outcome: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "finished_at_ms") val finishedAtMs: Long?,
)

@Dao
interface ExportJobDao {
    @Upsert suspend fun upsert(entity: ExportJobEntity)

    @Query("SELECT * FROM export_jobs ORDER BY started_at_ms DESC LIMIT 100")
    fun observeRecent(): Flow<List<ExportJobEntity>>

    @Query("UPDATE export_jobs SET outcome = :outcome, finished_at_ms = :atMs WHERE id = :id")
    suspend fun finish(id: String, outcome: String, atMs: Long)

    @Query("UPDATE export_jobs SET outcome = 'INTERRUPTED', finished_at_ms = :atMs WHERE outcome = 'RUNNING' AND started_at_ms < :processStartMs")
    suspend fun markInterrupted(processStartMs: Long, atMs: Long)

    @Query("DELETE FROM export_jobs WHERE outcome != 'RUNNING'")
    suspend fun clearFinished()
}
