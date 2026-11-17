package io.bsoft.bconverter.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.bsoft.bconverter.data.repository.RoomProjectRepository
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.project.ProjectLoadResult
import io.bsoft.bconverter.domain.project.StoredProject
import io.bsoft.bconverter.domain.timeline.ExportSettings
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.domain.timeline.VideoProject
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomProjectRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomProjectRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        repo = RoomProjectRepository(db.projectDao())
    }
    @After fun close() { db.close() }

    private fun stored(id: String, name: String = "P", modified: Long = 1, clips: Int = 1): StoredProject {
        val videos = (1..clips).map { VideoClip("$id-v$it", MediaSource.FileSource(File("/tmp/$it.mp4")), 10_000, 0, 10_000) }
        val export = ExportSettings(ContainerFormat.MP4, VideoSettings(VideoCodec.H264, VideoQuality.Crf(23)), AudioSettings(AudioCodec.AAC, 128))
        return StoredProject(VideoProject(id, name, Timeline(videos).normalized(), export), createdAtMs = 1, modifiedAtMs = modified)
    }

    @Test fun saved_project_loads_back_identically() = runTest {
        val s = stored("a", clips = 2)
        repo.save(s)
        assertEquals(ProjectLoadResult.Loaded(s), repo.load("a"))
    }

    @Test fun saving_again_updates_instead_of_duplicating() = runTest {
        repo.save(stored("a", name = "old"))
        repo.save(stored("a", name = "new", modified = 5))
        assertEquals(1, repo.list().size)
        assertEquals("new", (repo.load("a") as ProjectLoadResult.Loaded).stored.project.name)
    }

    @Test fun list_is_newest_first_with_summary_data() = runTest {
        repo.save(stored("old", modified = 10))
        repo.save(stored("new", modified = 20, clips = 3))
        val list = repo.list()
        assertEquals(listOf("new", "old"), list.map { it.id })
        assertEquals(3, list.first().clipCount)
        assertEquals(30_000, list.first().durationMs)
    }

    @Test fun delete_removes_the_project() = runTest {
        repo.save(stored("a"))
        repo.delete("a")
        assertEquals(ProjectLoadResult.NotFound, repo.load("a"))
        assertTrue(repo.list().isEmpty())
    }

    @Test fun unknown_id_is_not_found() = runTest { assertEquals(ProjectLoadResult.NotFound, repo.load("zzz")) }

    @Test fun a_damaged_payload_loads_as_corrupt_but_stays_listed_so_it_can_be_deleted() = runTest {
        db.projectDao().upsert(ProjectEntity("bad", "Broken", 1, 2, 0, 0, "{not json"))
        assertEquals(ProjectLoadResult.Corrupt, repo.load("bad"))
        assertEquals(listOf("bad"), repo.list().map { it.id })
        repo.delete("bad")
        assertTrue(repo.list().isEmpty())
    }
}
