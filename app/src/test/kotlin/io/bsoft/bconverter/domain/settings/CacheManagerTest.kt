package io.bsoft.bconverter.domain.settings

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheManagerTest {
    private val root = Files.createTempDirectory("cm").toFile().also { it.deleteOnExit() }
    private fun dir(name: String) = File(root, name).apply { mkdirs() }
    private fun File.put(name: String, bytes: Int) = File(this, name).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test fun usage_adds_up_files_in_every_folder_including_nested_ones() {
        val a = dir("a"); val b = dir("b")
        a.put("1", 100); a.put("sub/2", 50); b.put("3", 25)
        assertEquals(175L, CacheManager(listOf(a, b)).usageBytes())
    }

    @Test fun missing_folders_count_as_empty() {
        assertEquals(0L, CacheManager(listOf(File(root, "nope"))).usageBytes())
        assertEquals(0L, CacheManager(emptyList()).usageBytes())
    }

    @Test fun clearing_empties_the_folders_but_keeps_them_and_reports_what_was_freed() {
        val a = dir("a"); a.put("1", 100); a.put("sub/2", 50)
        assertEquals(150L, CacheManager(listOf(a)).clear())
        assertTrue(a.isDirectory); assertEquals(0, a.listFiles()!!.size)
    }

    @Test fun clearing_leaves_other_folders_alone() {
        val a = dir("a"); val other = dir("other"); a.put("1", 10); other.put("keep", 10)
        CacheManager(listOf(a)).clear()
        assertTrue(File(other, "keep").exists())
    }

    @Test fun clearing_nothing_frees_nothing() {
        assertEquals(0L, CacheManager(listOf(File(root, "nope"))).clear())
    }

    @Test fun usage_is_zero_after_clearing() {
        val a = dir("a"); a.put("1", 10); val m = CacheManager(listOf(a)); m.clear()
        assertFalse(m.usageBytes() > 0)
    }
}
