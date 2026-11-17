package io.bsoft.bconverter.data.storage

import io.bsoft.bconverter.domain.media.Waveform
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskWaveformStoreTest {
    private val dir = Files.createTempDirectory("ws").toFile().also { it.deleteOnExit() }
    private val store = DiskWaveformStore(File(dir, "w"))

    @Test fun an_unknown_key_is_a_miss() { assertNull(store.get("a")) }

    @Test fun a_stored_waveform_comes_back() {
        store.put("a", Waveform(listOf(0f, 1f), 20))
        assertEquals(2, store.get("a")!!.peaks.size)
    }

    @Test fun keys_are_independent_and_safe_as_file_names() {
        store.put("../../x|1", Waveform(listOf(1f), 20)); store.put("y", Waveform(listOf(0f, 0f), 20))
        assertEquals(1, store.get("../../x|1")!!.peaks.size); assertEquals(2, store.get("y")!!.peaks.size)
        assertTrue(File(dir, "w").listFiles()!!.all { it.parentFile == File(dir, "w") })
    }

    @Test fun a_damaged_entry_is_a_miss() {
        store.put("a", Waveform(listOf(1f), 20))
        File(dir, "w").listFiles()!!.single().writeBytes(byteArrayOf(9))
        assertNull(store.get("a"))
    }
}
