package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.fakes.FakeProjectRepository
import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.fakes.TL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectManagerTest {
    private val repo = FakeProjectRepository()
    private var now = 5_000L
    private val manager = ProjectManager(repo, SequentialIds()) { now }
    private val timeline = TL.timeline(TL.video("v"))

    private fun seed(id: String, name: String = "Holiday") = runBlocking {
        repo.save(StoredProject(TL.project(timeline).copy(id = id, name = name), createdAtMs = 1_000, modifiedAtMs = 2_000))
    }
    private fun stored(id: String) = (runBlocking { repo.load(id) } as ProjectLoadResult.Loaded).stored
    private fun names() = runBlocking { repo.list() }.map { it.name }

    // ---- naming ----
    @Test fun a_copy_is_named_after_the_original() { assertEquals("Holiday copy", ProjectNaming.copyName("Holiday", emptySet())) }
    @Test fun a_taken_copy_name_gets_the_next_number() {
        assertEquals("Holiday copy 2", ProjectNaming.copyName("Holiday", setOf("Holiday copy")))
        assertEquals("Holiday copy 3", ProjectNaming.copyName("Holiday", setOf("Holiday copy", "Holiday copy 2")))
    }
    @Test fun copying_a_copy_does_not_pile_up_suffixes() {
        assertEquals("Holiday copy 2", ProjectNaming.copyName("Holiday copy", setOf("Holiday copy")))
        assertEquals("Holiday copy 3", ProjectNaming.copyName("Holiday copy 2", setOf("Holiday copy", "Holiday copy 2")))
    }
    @Test fun a_very_long_name_is_shortened_so_the_copy_stays_valid() {
        val copy = ProjectNaming.copyName("x".repeat(200), emptySet())
        assertTrue(copy.length <= 200); assertTrue(copy.endsWith(" copy"))
    }

    // ---- renaming ----
    @Test fun renaming_changes_only_the_name_and_the_modified_time() {
        seed("a"); assertEquals(ProjectActionResult.Success, runBlocking { manager.rename("a", "  Summer trip ") })
        val s = stored("a")
        assertEquals("Summer trip", s.project.name); assertEquals(5_000L, s.modifiedAtMs); assertEquals(1_000L, s.createdAtMs)
        assertEquals(timeline, s.project.timeline)
    }
    @Test fun an_invalid_name_is_rejected_and_nothing_is_saved() {
        seed("a"); val before = repo.saveCount
        assertEquals(ProjectActionResult.InvalidName, runBlocking { manager.rename("a", "bad/name") })
        assertEquals(ProjectActionResult.InvalidName, runBlocking { manager.rename("a", "   ") })
        assertEquals("Holiday", stored("a").project.name); assertEquals(before, repo.saveCount)
    }
    @Test fun renaming_an_unknown_project_reports_not_found() { assertEquals(ProjectActionResult.NotFound, runBlocking { manager.rename("nope", "X") }) }
    @Test fun renaming_a_damaged_project_reports_it() { seed("a"); repo.corrupt("a"); assertEquals(ProjectActionResult.Corrupt, runBlocking { manager.rename("a", "X") }) }
    @Test fun a_failed_save_keeps_the_old_name() {
        seed("a"); repo.failNextSave = true
        assertEquals(ProjectActionResult.Failed, runBlocking { manager.rename("a", "New") })
        assertEquals("Holiday", stored("a").project.name)
    }

    // ---- duplicating ----
    @Test fun duplicating_saves_a_second_project_with_the_same_content_and_a_new_id() {
        seed("a"); assertEquals(ProjectActionResult.Success, runBlocking { manager.duplicate("a") })
        val copy = stored("id-1")
        assertEquals("Holiday copy", copy.project.name); assertEquals(timeline, copy.project.timeline)
        assertEquals("id-1", copy.project.id); assertEquals(5_000L, copy.createdAtMs); assertEquals(5_000L, copy.modifiedAtMs)
        assertEquals(stored("a").project.export, copy.project.export)
    }
    @Test fun duplicating_leaves_the_original_untouched() {
        seed("a"); runBlocking { manager.duplicate("a") }
        val original = stored("a"); assertEquals("Holiday", original.project.name); assertEquals(2_000L, original.modifiedAtMs)
    }
    @Test fun a_second_duplicate_gets_a_numbered_name() {
        seed("a"); runBlocking { manager.duplicate("a"); manager.duplicate("a") }
        assertEquals(setOf("Holiday", "Holiday copy", "Holiday copy 2"), names().toSet())
    }
    @Test fun duplicating_an_unknown_or_damaged_project_reports_it() {
        assertEquals(ProjectActionResult.NotFound, runBlocking { manager.duplicate("nope") })
        seed("a"); repo.corrupt("a"); assertEquals(ProjectActionResult.Corrupt, runBlocking { manager.duplicate("a") })
    }
    @Test fun a_failed_save_adds_no_project() {
        seed("a"); repo.failNextSave = true
        assertEquals(ProjectActionResult.Failed, runBlocking { manager.duplicate("a") })
        assertEquals(listOf("Holiday"), names())
    }
}
