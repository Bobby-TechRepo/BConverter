package io.bsoft.bconverter.feature.pickinput

import android.net.Uri
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.fakes.FakeInputResolver
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PickInputViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val resolver = FakeInputResolver()
    private val uri: Uri = Uri.parse("content://x/1")
    private val staged = ResolvedInput(File("/tmp/a.mp4"), "a.mp4", 10, isStaged = true)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun vm() = PickInputViewModel(resolver)

    @Test fun starts_idle() { assertEquals(PickInputState.Idle, vm().state.value) }

    @Test fun cancelled_picker_result_is_ignored() = runTest(dispatcher) {
        val vm = vm(); vm.onPicked(null); runCurrent()
        assertEquals(PickInputState.Idle, vm.state.value); assertTrue(resolver.resolved.isEmpty())
    }

    @Test fun shows_resolving_while_the_resolver_works_then_ready() = runTest(dispatcher) {
        resolver.gate = CompletableDeferred()
        resolver.result = ResolveResult.Success(staged)
        val vm = vm(); vm.onPicked(uri); runCurrent()
        assertEquals(PickInputState.Resolving, vm.state.value)
        resolver.gate!!.complete(Unit); runCurrent()
        assertEquals(PickInputState.Ready(staged), vm.state.value)
    }

    @Test fun resolves_the_picked_uri_as_a_uri_source() = runTest(dispatcher) {
        val vm = vm(); vm.onPicked(uri); runCurrent()
        assertEquals(listOf<MediaSource>(MediaSource.UriSource(uri)), resolver.resolved)
    }

    @Test fun resolver_failure_becomes_error_state() = runTest(dispatcher) {
        resolver.result = ResolveResult.Failure(InputError.PermissionDenied)
        val vm = vm(); vm.onPicked(uri); runCurrent()
        assertEquals(PickInputState.Error(InputError.PermissionDenied), vm.state.value)
    }

    @Test fun picking_again_releases_the_previous_staged_input() = runTest(dispatcher) {
        resolver.result = ResolveResult.Success(staged)
        val vm = vm(); vm.onPicked(uri); runCurrent()
        vm.onPicked(Uri.parse("content://x/2")); runCurrent()
        assertEquals(listOf(staged), resolver.released)
    }

    @Test fun reset_releases_staged_input_and_returns_to_idle() = runTest(dispatcher) {
        resolver.result = ResolveResult.Success(staged)
        val vm = vm(); vm.onPicked(uri); runCurrent()
        vm.reset()
        assertEquals(PickInputState.Idle, vm.state.value); assertEquals(listOf(staged), resolver.released)
    }
}
