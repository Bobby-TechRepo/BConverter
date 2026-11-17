package io.bsoft.bconverter.domain.about

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every bundled licence points at a real, non-trivial text in assets/licenses (step 5 of the implementation plan). */
class BundledLicensesTest {
    private val dir: File = listOf("src/main/assets/licenses", "app/src/main/assets/licenses").map(::File).first { it.isDirectory }

    @Test fun every_licence_file_exists_and_has_text() {
        BundledLicenses.all.flatMap { it.assetFiles }.forEach { name ->
            val f = File(dir, name)
            assertTrue("$name is missing", f.isFile)
            assertTrue("$name looks empty", f.readText().length > 500)
        }
    }

    @Test fun the_lgpl_3_ships_with_the_gpl_3_it_incorporates() {
        val lgpl3 = BundledLicenses.all.single { it.title.contains("3.0") && it.title.contains("Lesser") }
        assertTrue(lgpl3.assetFiles.containsAll(listOf("lgpl-3.0.txt", "gpl-3.0.txt")))
    }

    @Test fun the_font_licence_carries_its_copyright_line() {
        assertTrue(File(dir, "ofl-1.1.txt").readText().startsWith("Copyright"))
    }
}
