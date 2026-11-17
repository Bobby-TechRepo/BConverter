package io.bsoft.bconverter.data.storage

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OverlayImageCleanerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val now = 1_000_000_000_000L
    private val old = now - 48L * 3_600_000

    private fun picture(dir: File, name: String, modified: Long) = File(dir, name).apply { writeText("x"); setLastModified(modified) }

    @Test fun removes_only_old_unused_pictures() = runBlocking {
        val dir = tmp.newFolder("imgs")
        val used = picture(dir, "img-1.png", old)
        val unused = picture(dir, "img-2.png", old)
        val fresh = picture(dir, "img-3.png", now)
        val other = picture(dir, "keep.txt", old)
        val removed = OverlayImageCleaner({ listOf("""{"p":"${used.absolutePath}"}""") }, dir) { now }.clean()
        assertEquals(1, removed)
        assertTrue(used.exists()); assertFalse(unused.exists()); assertTrue(fresh.exists()); assertTrue(other.exists())
    }

    @Test fun a_missing_folder_is_nothing_to_do() = runBlocking {
        assertEquals(0, OverlayImageCleaner({ emptyList() }, File(tmp.root, "none")) { now }.clean())
    }
}
