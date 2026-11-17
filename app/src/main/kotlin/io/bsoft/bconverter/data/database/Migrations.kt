package io.bsoft.bconverter.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 adds the export job history (step 17). Written by hand so it does not depend on the version-1 schema file
 * having been committed; the SQL matches what Room generates for [ExportJobEntity] (Room checks it on open).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `export_jobs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`destination` TEXT NOT NULL, `outcome` TEXT NOT NULL, `started_at_ms` INTEGER NOT NULL, `finished_at_ms` INTEGER, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
