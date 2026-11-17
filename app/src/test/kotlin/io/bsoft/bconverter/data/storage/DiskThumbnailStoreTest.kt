package io.bsoft.bconverter.data.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskThumbnailStoreTest {
    private val dir = Files.createTempDirectory("thumbs").toFile().also { it.deleteOnExit() }
    private fun store(maxBytes: Long = 1_000) = DiskThumbnailStore(dir, maxBytes)
    private fun File.fill(bytes: Int, age: Long) = apply { writeBytes(ByteArray(bytes)); setLastModified(System.currentTimeMillis() - age) }

    @Test fun the_same_key_always_maps_to_the_same_file_inside_the_directory() {
        val s = store()
        assertEquals(s.fileFor("a|1|2|500|120"), s.fileFor("a|1|2|500|120"))
        assertEquals(dir, s.fileFor("a|1|2|500|120").parentFile)
        assertTrue(s.fileFor("a").name.endsWith(".jpg"))
    }

    @Test fun different_keys_map_to_different_files() {
        val s = store()
        assertNotEquals(s.fileFor("a|500"), s.fileFor("a|1000"))
    }

    @Test fun keys_with_path_characters_stay_in_the_directory() {
        assertEquals(dir, store().fileFor("../../etc/passwd|/x/y").parentFile)
    }

    @Test fun trimming_removes_the_oldest_files_until_under_the_limit() {
        val s = store(maxBytes = 250)
        val old = s.fileFor("old").fill(100, age = 3_000)
        val mid = s.fileFor("mid").fill(100, age = 2_000)
        val new = s.fileFor("new").fill(100, age = 1_000)
        assertEquals(1, s.trim())
        assertFalse(old.exists()); assertTrue(mid.exists()); assertTrue(new.exists())
    }

    @Test fun trimming_below_the_limit_removes_nothing() {
        val s = store(maxBytes = 1_000)
        s.fileFor("a").fill(100, 10)
        assertEquals(0, s.trim())
    }

    @Test fun trimming_an_empty_or_missing_directory_is_harmless() {
        assertEquals(0, store().trim())
        assertEquals(0, DiskThumbnailStore(File(dir, "nope"), 10).trim())
    }

    @Test fun clear_removes_everything() {
        val s = store()
        s.fileFor("a").fill(10, 0); s.fileFor("b").fill(10, 0)
        s.clear()
        assertEquals(0, dir.listFiles()!!.size)
    }
}
