package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.FakeProjectRepository
import io.bsoft.bconverter.fakes.TL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAutosaverTest {
    private val repo = FakeProjectRepository()
    private var now = 1_000L
    private val autosaver = ProjectAutosaver(repo) { now }
    private val project = TL.project(TL.timeline(TL.video("v1")))

    private fun stored(id: String = "p1") = (runBlocking { repo.load(id) } as ProjectLoadResult.Loaded).stored

    @Test fun the_first_save_stamps_creation_and_modification_time() = runBlocking {
        assertTrue(autosaver.save(project) is AutosaveResult.Saved)
        assertEquals(1_000, stored().createdAtMs)
        assertEquals(1_000, stored().modifiedAtMs)
    }

    @Test fun a_later_edit_keeps_the_creation_time_and_updates_the_modification_time() = runBlocking {
        autosaver.save(project)
        now = 5_000
        autosaver.save(project.copy(timeline = TL.timeline(TL.video("v1"), TL.video("v2"))))
        assertEquals(1_000, stored().createdAtMs)
        assertEquals(5_000, stored().modifiedAtMs)
        assertEquals(2, stored().project.timeline.videoClips.size)
    }

    @Test fun saving_an_unchanged_project_writes_nothing() = runBlocking {
        autosaver.save(project)
        now = 9_000
        assertEquals(AutosaveResult.Unchanged, autosaver.save(project))
        assertEquals(1, repo.saveCount)
        assertEquals(1_000, stored().modifiedAtMs)
    }

    @Test fun source_metadata_alone_does_not_count_as_a_change() = runBlocking {
        autosaver.save(project)
        assertEquals(AutosaveResult.Unchanged, autosaver.save(project.copy(sourceMetadata = emptyMap())))
    }

    @Test fun an_interrupted_save_reports_failure_and_keeps_the_previous_version() = runBlocking {
        autosaver.save(project)
        repo.failNextSave = true
        val result = autosaver.save(project.copy(name = "Renamed"))
        assertTrue(result is AutosaveResult.Failed)
        assertEquals("Project", stored().project.name)
    }

    @Test fun after_a_failed_save_the_next_attempt_writes_the_pending_change() = runBlocking {
        autosaver.save(project)
        repo.failNextSave = true
        val edited = project.copy(name = "Renamed")
        autosaver.save(edited)
        assertTrue(autosaver.save(edited) is AutosaveResult.Saved)
        assertEquals("Renamed", stored().project.name)
    }

    @Test fun a_first_save_that_fails_can_be_retried_with_the_same_creation_time() = runBlocking {
        repo.failNextSave = true
        assertTrue(autosaver.save(project) is AutosaveResult.Failed)
        now = 4_000
        autosaver.save(project)
        assertEquals(4_000, stored().createdAtMs)
    }

    @Test fun an_autosaver_resumed_for_an_existing_project_keeps_its_original_creation_time() = runBlocking {
        repo.save(StoredProject(project, createdAtMs = 100, modifiedAtMs = 200))
        val resumed = ProjectAutosaver(repo) { 7_000 }
        resumed.resume(stored())
        resumed.save(project.copy(timeline = Timeline()))
        assertEquals(100, stored().createdAtMs)
        assertEquals(7_000, stored().modifiedAtMs)
    }

    @Test fun resuming_without_changes_writes_nothing() = runBlocking {
        repo.save(StoredProject(project, 100, 200))
        val resumed = ProjectAutosaver(repo) { 7_000 }
        resumed.resume(stored())
        assertEquals(AutosaveResult.Unchanged, resumed.save(project))
    }
}
