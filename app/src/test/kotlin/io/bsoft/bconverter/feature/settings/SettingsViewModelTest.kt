package io.bsoft.bconverter.feature.settings

import io.bsoft.bconverter.domain.settings.AppSettings
import io.bsoft.bconverter.domain.settings.CacheManager
import io.bsoft.bconverter.domain.settings.ThemeMode
import io.bsoft.bconverter.fakes.FakeSettingsRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeSettingsRepository()
    private val folder = Files.createTempDirectory("svm").toFile().also { it.deleteOnExit() }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun fill(bytes: Int) = File(folder, "f${System.nanoTime()}").writeBytes(ByteArray(bytes))
    private fun vm() = SettingsViewModel(repo, CacheManager(listOf(folder)), dispatcher)

    @Test fun shows_the_stored_settings() = runTest(dispatcher) {
        val repo2 = FakeSettingsRepository(AppSettings(ThemeMode.DARK, false))
        val vm = SettingsViewModel(repo2, CacheManager(listOf(folder)), dispatcher); runCurrent()
        assertEquals(AppSettings(ThemeMode.DARK, false), vm.state.value.settings)
    }

    @Test fun changing_the_theme_is_saved_and_shown() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        vm.setThemeMode(ThemeMode.LIGHT); runCurrent()
        assertEquals(ThemeMode.LIGHT, repo.settings.value.themeMode); assertEquals(ThemeMode.LIGHT, vm.state.value.settings.themeMode)
    }

    @Test fun dynamic_colour_can_be_switched() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        assertFalse(vm.state.value.settings.dynamicColor) // brand colours by default
        vm.setDynamicColor(true); runCurrent()
        assertTrue(vm.state.value.settings.dynamicColor)
    }

    @Test fun the_cache_size_is_measured_on_start() = runTest(dispatcher) {
        fill(300); val vm = vm(); runCurrent()
        assertEquals(300L, vm.state.value.cacheBytes)
    }

    @Test fun clearing_reports_what_was_freed_and_the_new_size() = runTest(dispatcher) {
        fill(300); val vm = vm(); runCurrent()
        vm.clearCache(); runCurrent()
        assertEquals(300L, vm.state.value.freedBytes); assertEquals(0L, vm.state.value.cacheBytes)
        assertFalse(vm.state.value.clearing); assertFalse(vm.state.value.clearFailed)
    }

    @Test fun a_new_clear_forgets_the_previous_message() = runTest(dispatcher) {
        fill(10); val vm = vm(); runCurrent(); vm.clearCache(); runCurrent()
        vm.clearCache()
        assertNull(vm.state.value.freedBytes); assertTrue(vm.state.value.clearing)
        runCurrent()
    }

    @Test fun clearing_an_already_empty_cache_is_not_an_error() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.clearCache(); runCurrent()
        assertEquals(0L, vm.state.value.freedBytes); assertFalse(vm.state.value.clearFailed)
    }

    @Test fun a_failing_cache_is_reported_not_thrown() = runTest(dispatcher) {
        val broken = SettingsViewModel(repo, object : io.bsoft.bconverter.domain.settings.CacheStorage {
            override fun usageBytes(): Long = 5
            override fun clear(): Long = throw java.io.IOException("disk")
        }, dispatcher)
        runCurrent(); broken.clearCache(); runCurrent()
        assertTrue(broken.state.value.clearFailed); assertFalse(broken.state.value.clearing)
    }
}
