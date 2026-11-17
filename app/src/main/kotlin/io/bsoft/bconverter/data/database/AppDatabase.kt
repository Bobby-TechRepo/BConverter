package io.bsoft.bconverter.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Migration policy: never bump [version] without a migration. Prefer `@AutoMigration(from = N, to = N + 1)` in
 * `autoMigrations` (Room diffs the exported schemas in `app/schemas`); write a manual `Migration` only for data changes,
 * and cover each one with `MigrationTestHelper` in androidTest. Destructive fallback is never used: projects are user data.
 * The project payload is versioned JSON (ProjectSerializer), so most model changes need no schema change at all.
 *
 * Version 2 (step 17): export job history, see [MIGRATION_1_2].
 */
@Database(entities = [ProjectEntity::class, ExportJobEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun exportJobDao(): ExportJobDao
}
