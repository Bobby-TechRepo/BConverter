package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageGuardTest {
    @get:Rule val tmp = TemporaryFolder()
    private val free = FakeFreeSpaceProvider()
    private fun guard() = StorageGuard(free, StagingDirectory(File(tmp.root, "staging")))
    private val required = 1_000 + StorageGuard.HEADROOM_BYTES

    @Test fun enough_space_is_sufficient() { free.available = required; assertEquals(StorageCheck.Sufficient, guard().check(1_000)) }

    @Test fun too_little_space_reports_what_is_needed_and_available() {
        free.available = required - 1
        assertEquals(StorageCheck.Insufficient(required, required - 1), guard().check(1_000))
    }

    @Test fun the_staging_volume_is_the_one_that_is_measured() {
        val g = guard(); g.check(1); assertEquals(File(tmp.root, "staging"), free.lastDir)
    }
}
