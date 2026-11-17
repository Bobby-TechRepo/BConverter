package io.bsoft.bconverter.feature.jobs

import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.JobKind
import io.bsoft.bconverter.domain.usecase.JobOutcome
import io.bsoft.bconverter.domain.usecase.JobRecord
import io.bsoft.bconverter.fakes.FakeJobHistory
import io.bsoft.bconverter.fakes.RecordingExportScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scheduler = RecordingExportScheduler()
    private val registry = ExportRegistry()
    private val history = FakeJobHistory(
        listOf(
            JobRecord("run", "Running", JobKind.RENDER, "content://d/1", JobOutcome.RUNNING, 20),
            JobRecord("done", "Done", JobKind.CONVERT, "content://d/2", JobOutcome.COMPLETED, 10, 15),
        ),
    )
    private fun vm() = JobsViewModel(history, registry, ExportJobLauncher(registry, scheduler))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun lists_active_and_finished_jobs() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        assertEquals(listOf("run"), vm.state.value.active.map { it.id })
        assertEquals(listOf("done"), vm.state.value.finished.map { it.id })
    }

    @Test fun cancelling_goes_through_the_scheduler() = runTest(dispatcher) {
        vm().cancel("run")
        assertEquals(listOf("run"), scheduler.cancelled)
    }

    @Test fun clearing_keeps_only_running_jobs() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect() }
        vm.clearFinished()
        assertTrue(vm.state.value.finished.isEmpty())
        assertEquals(listOf("run"), vm.state.value.active.map { it.id })
    }

    @Test fun a_read_failure_is_reported_instead_of_crashing() = runTest(dispatcher) {
        val failing = object : io.bsoft.bconverter.domain.usecase.JobHistory by history {
            override fun records() = kotlinx.coroutines.flow.flow<List<JobRecord>> { throw IllegalStateException("disk") }
        }
        val vm = JobsViewModel(failing, registry, ExportJobLauncher(registry, scheduler))
        backgroundScope.launch { vm.state.collect() }
        assertTrue(vm.state.value.loadFailed)
    }

    @Test fun unused_status_is_ignored() {
        // A live status for a job the history does not know (yet) adds no row.
        assertTrue(JobItems.state(emptyList(), mapOf("x" to JobStatus.Queued)).isEmpty)
    }
}
