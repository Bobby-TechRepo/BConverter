package io.bsoft.bconverter.data.storage

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 15 on a real device: the symlink to /proc/self/fd reads the document's own bytes, and closing removes it. */
class AndroidInPlaceOpenerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun a_local_file_is_read_through_the_link_without_a_copy() {
        val dir = File(context.cacheDir, "inplace-test").apply { deleteRecursively(); mkdirs() }
        val source = File(dir, "source.bin").apply { writeBytes(ByteArray(4096) { (it % 251).toByte() }) }
        val link = File(dir, "link.bin")
        val handle = AndroidInPlaceOpener(context.contentResolver).open(Uri.fromFile(source), link)
        assertNotNull("a regular local file should be readable in place", handle)
        assertArrayEquals(source.readBytes(), handle!!.file.readBytes())
        assertTrue(java.nio.file.Files.isSymbolicLink(link.toPath()))
        handle.close()
        assertFalse(link.exists())
        dir.deleteRecursively()
    }

    @Test fun a_missing_document_falls_back_to_null() {
        val dir = File(context.cacheDir, "inplace-test2").apply { mkdirs() }
        val missing = Uri.fromFile(File(dir, "nope.bin"))
        assertTrue(AndroidInPlaceOpener(context.contentResolver).open(missing, File(dir, "link.bin")) == null)
        dir.deleteRecursively()
    }
}
