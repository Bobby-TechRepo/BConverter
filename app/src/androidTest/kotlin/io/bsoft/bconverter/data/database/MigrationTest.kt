package io.bsoft.bconverter.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A version-1 database (projects only), written by hand the way Room laid it out, opens as version 2 with
 * [MIGRATION_1_2]: projects are kept and the job history table works. Room validates the migrated schema on open,
 * so a mismatch between the migration SQL and [ExportJobEntity] fails here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Before @After fun clean() { context.deleteDatabase(name) }

    @Test fun version_1_upgrades_to_2_keeping_projects() = runTest {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at_ms` INTEGER NOT NULL, " +
                    "`modified_at_ms` INTEGER NOT NULL, `clip_count` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL("INSERT INTO projects VALUES ('p1', 'Holiday', 1, 2, 3, 4000, '{}')")
            v1.version = 1
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals(listOf("Holiday"), db.projectDao().summaries().map { it.name })
            db.exportJobDao().upsert(ExportJobEntity("j1", "Clip", "CONVERT", "content://d/1", "RUNNING", 10, null))
            db.exportJobDao().markInterrupted(processStartMs = 20, atMs = 30)
            val row = db.exportJobDao().observeRecent().first().single()
            assertEquals("INTERRUPTED", row.outcome); assertEquals(30L, row.finishedAtMs)
        } finally {
            db.close()
        }
    }
}
