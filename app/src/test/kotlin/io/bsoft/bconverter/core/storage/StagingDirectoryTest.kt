package io.bsoft.bconverter.core.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StagingDirectoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun dir() = StagingDirectory(File(tmp.root, "staging"))

    @Test fun creates_missing_root_directory() = assertTrue(dir().root.isDirectory)

    @Test fun new_file_is_a_direct_child_of_root() {
        val d = dir(); assertEquals(d.root, d.newFile("a.mp4").parentFile)
    }

    @Test fun new_file_is_only_a_reserved_path_not_created() = assertFalse(dir().newFile("a.mp4").exists())

    @Test fun same_name_yields_distinct_paths() {
        val d = dir(); assertNotEquals(d.newFile("a.mp4"), d.newFile("a.mp4"))
    }

    @Test fun forward_slash_traversal_is_neutralised() {
        val d = dir(); val f = d.newFile("../../evil.mp4")
        assertEquals(d.root, f.parentFile); assertTrue(f.name.endsWith("evil.mp4"))
    }

    @Test fun backslash_traversal_is_neutralised() {
        val d = dir(); val f = d.newFile("..\\..\\evil.mp4")
        assertEquals(d.root, f.parentFile); assertTrue(f.name.endsWith("evil.mp4"))
    }

    @Test fun unsafe_characters_are_replaced() =
        assertTrue(dir().newFile("my clip?.mp4").name.endsWith("my_clip_.mp4"))

    @Test fun extension_is_preserved() = assertTrue(dir().newFile("My Video.MP4").name.endsWith(".MP4"))

    @Test fun null_blank_and_dot_only_names_fall_back_to_input() {
        val d = dir()
        listOf(null, "  ", "..").forEach { assertTrue(d.newFile(it).name.endsWith("_input")) }
    }

    @Test fun very_long_names_are_shortened_but_keep_extension() {
        val name = dir().newFile("a".repeat(300) + ".mp4").name
        assertTrue(name.endsWith(".mp4")); assertTrue(name.length < 200)
    }

    @Test fun clear_removes_everything() {
        val d = dir()
        d.newFile("a.mp4").writeText("x"); d.newFile("b.mp4").writeText("y")
        d.clear()
        assertTrue(d.listFiles().isEmpty())
    }

    @Test fun clear_older_than_removes_only_stale_files() {
        val d = dir()
        val old = d.newFile("old.mp4").apply { writeText("x"); setLastModified(1_000) }
        val fresh = d.newFile("new.mp4").apply { writeText("y"); setLastModified(5_000) }
        d.clearOlderThan(3_000)
        assertEquals(listOf(fresh), d.listFiles()); assertFalse(old.exists())
    }
}
