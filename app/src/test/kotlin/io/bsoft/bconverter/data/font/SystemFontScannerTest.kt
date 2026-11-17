package io.bsoft.bconverter.data.font

import io.bsoft.bconverter.domain.font.FontSource
import io.bsoft.bconverter.domain.font.FontVariant
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemFontScannerTest {
    private val root = Files.createTempDirectory("sf").toFile()
    private val a = File(root, "a").apply { mkdirs() }
    private val b = File(root, "b").apply { mkdirs() }
    @After fun tearDown() { root.deleteRecursively() }

    @Test fun fonts_are_found_by_file_name_and_other_files_ignored() {
        listOf("Roboto-Regular.ttf", "Roboto-Bold.ttf", "NotoColorEmoji.ttf", "fonts.xml").forEach { File(a, it).writeText("x") }
        val r = SystemFontScanner(listOf(a)).scan()
        assertEquals(setOf(FontVariant(400), FontVariant(700)), r.map { it.variant }.toSet())
        assertTrue(r.all { it.family == "Roboto" && it.source == FontSource.SYSTEM })
    }
    @Test fun the_same_font_in_two_folders_is_listed_once() {
        File(a, "Lobster-Regular.ttf").writeText("x"); File(b, "Lobster-Regular.ttf").writeText("x")
        assertEquals(1, SystemFontScanner(listOf(a, b)).scan().size)
    }
    @Test fun a_missing_folder_gives_nothing_and_does_not_fail() = assertTrue(SystemFontScanner(listOf(File(root, "nope"))).scan().isEmpty())
}
