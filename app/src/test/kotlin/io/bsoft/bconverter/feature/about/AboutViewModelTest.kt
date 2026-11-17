package io.bsoft.bconverter.feature.about

import io.bsoft.bconverter.domain.about.AppInfo
import io.bsoft.bconverter.domain.about.AppInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun the_info_arrives_after_loading() = runTest(dispatcher) {
        val vm = AboutViewModel({ AppInfo("1.0", 3, "n8") }, dispatcher)
        assertNull(vm.info.value); runCurrent()
        assertEquals(AppInfo("1.0", 3, "n8"), vm.info.value)
    }

    @Test fun a_failing_provider_still_gives_a_screen() = runTest(dispatcher) {
        val vm = AboutViewModel(AppInfoProvider { throw IllegalStateException("no native lib") }, dispatcher); runCurrent()
        assertEquals("unknown", vm.info.value!!.versionName)
    }
}
