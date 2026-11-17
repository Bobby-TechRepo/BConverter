package io.bsoft.bconverter.feature.projects

import io.bsoft.bconverter.domain.project.ProjectActionResult
import io.bsoft.bconverter.domain.project.ProjectManager
import io.bsoft.bconverter.domain.project.StoredProject
import io.bsoft.bconverter.fakes.SequentialIds
import io.bsoft.bconverter.fakes.FakeProjectRepository
import io.bsoft.bconverter.fakes.TL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeProjectRepository()
    private fun vm() = ProjectsViewModel(repo, ProjectManager(repo, SequentialIds()) { 9_000L })

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun seed(id: String, modified: Long) = runBlocking {
        repo.save(StoredProject(TL.project(TL.timeline(TL.video("v"))).copy(id = id, name = "Project $id"), 1, modified))
    }

    @Test fun starts_in_a_loading_state() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.state.value.loading)
    }

    @Test fun lists_saved_projects_newest_first() = runTest(dispatcher) {
        seed("old", 10); seed("new", 20)
        val vm = vm(); runCurrent()
        assertFalse(vm.state.value.loading)
        assertEquals(listOf("new", "old"), vm.state.value.projects.map { it.id })
    }

    @Test fun no_projects_gives_an_empty_list_not_an_error() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertTrue(vm.state.value.projects.isEmpty()); assertFalse(vm.state.value.loading); assertFalse(vm.state.value.loadFailed)
    }

    @Test fun a_storage_failure_is_reported_instead_of_crashing() = runTest(dispatcher) {
        repo.failList = true
        val vm = vm(); runCurrent()
        assertTrue(vm.state.value.loadFailed); assertFalse(vm.state.value.loading)
    }

    @Test fun retrying_after_a_failure_loads_the_list() = runTest(dispatcher) {
        seed("a", 1); repo.failList = true
        val vm = vm(); runCurrent()
        repo.failList = false; vm.refresh(); runCurrent()
        assertFalse(vm.state.value.loadFailed); assertEquals(listOf("a"), vm.state.value.projects.map { it.id })
    }

    @Test fun deleting_removes_the_project_from_storage_and_the_list() = runTest(dispatcher) {
        seed("a", 1); seed("b", 2)
        val vm = vm(); runCurrent()
        vm.delete("b"); runCurrent()
        assertEquals(listOf("a"), vm.state.value.projects.map { it.id })
        assertEquals(listOf("a"), runBlocking { repo.list() }.map { it.id })
    }

    @Test fun a_failed_delete_keeps_the_project_listed_and_says_so() = runTest(dispatcher) {
        seed("a", 1)
        val vm = vm(); runCurrent()
        repo.failDelete = true; vm.delete("a"); runCurrent()
        assertEquals(listOf("a"), vm.state.value.projects.map { it.id }); assertTrue(vm.state.value.deleteFailed)
        vm.dismissDeleteFailed(); assertFalse(vm.state.value.deleteFailed)
    }

    // ---- file management: rename and duplicate ----
    @Test fun renaming_a_project_updates_the_list() = runTest(dispatcher) {
        seed("a", 10); val vm = vm(); runCurrent()
        vm.rename("a", "Summer trip"); runCurrent()
        assertEquals(listOf("Summer trip"), vm.state.value.projects.map { it.name }); assertEquals(null, vm.state.value.actionError)
    }

    @Test fun an_invalid_new_name_is_reported_and_the_name_is_kept() = runTest(dispatcher) {
        seed("a", 10); val vm = vm(); runCurrent()
        vm.rename("a", "bad/name"); runCurrent()
        assertEquals(ProjectActionResult.InvalidName, vm.state.value.actionError); assertEquals(listOf("Project a"), vm.state.value.projects.map { it.name })
    }

    @Test fun duplicating_adds_a_copy_to_the_list() = runTest(dispatcher) {
        seed("a", 10); val vm = vm(); runCurrent()
        vm.duplicate("a"); runCurrent()
        assertEquals(setOf("Project a", "Project a copy"), vm.state.value.projects.map { it.name }.toSet())
        assertEquals("Project a copy", vm.state.value.projects.first().name) // newest first
    }

    @Test fun a_failed_duplicate_is_reported() = runTest(dispatcher) {
        seed("a", 10); val vm = vm(); runCurrent()
        repo.failNextSave = true; vm.duplicate("a"); runCurrent()
        assertEquals(ProjectActionResult.Failed, vm.state.value.actionError); assertEquals(1, vm.state.value.projects.size)
    }

    @Test fun a_project_that_vanished_is_reported_and_the_list_refreshed() = runTest(dispatcher) {
        seed("a", 10); val vm = vm(); runCurrent()
        runBlocking { repo.delete("a") }; vm.duplicate("a"); runCurrent()
        assertEquals(ProjectActionResult.NotFound, vm.state.value.actionError); assertTrue(vm.state.value.projects.isEmpty())
    }

    @Test fun the_action_error_can_be_dismissed() = runTest(dispatcher) {
        seed("a", 10); val vm = vm(); runCurrent()
        vm.rename("a", " "); runCurrent(); vm.dismissActionError()
        assertEquals(null, vm.state.value.actionError)
    }
}
