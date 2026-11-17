package io.bsoft.bconverter.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per project. The summary columns let the project list load without decoding [payload] (the serialized project). */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "modified_at_ms") val modifiedAtMs: Long,
    @ColumnInfo(name = "clip_count") val clipCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val payload: String,
)

data class ProjectSummaryRow(
    val id: String,
    val name: String,
    @ColumnInfo(name = "modified_at_ms") val modifiedAtMs: Long,
    @ColumnInfo(name = "clip_count") val clipCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)
