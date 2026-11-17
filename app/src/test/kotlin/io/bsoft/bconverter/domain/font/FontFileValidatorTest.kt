package io.bsoft.bconverter.domain.font

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontFileValidatorTest {
    private fun data(magic: ByteArray, size: Int = 4096) = ByteArray(size).also { magic.copyInto(it) }
    @Test fun truetype_opentype_and_collections_pass() {
        assertTrue(FontFileValidator.isUsableFont(data(byteArrayOf(0, 1, 0, 0)))); assertTrue(FontFileValidator.isUsableFont(data("OTTO".toByteArray())))
        assertTrue(FontFileValidator.isUsableFont(data("true".toByteArray()))); assertTrue(FontFileValidator.isUsableFont(data("ttcf".toByteArray())))
    }
    @Test fun html_woff_and_tiny_files_fail() {
        assertFalse(FontFileValidator.isUsableFont(data("<!DO".toByteArray()))); assertFalse(FontFileValidator.isUsableFont(data("wOFF".toByteArray())))
        assertFalse(FontFileValidator.isUsableFont(data(byteArrayOf(0, 1, 0, 0), 100))); assertFalse(FontFileValidator.isUsableFont(ByteArray(0)))
    }
    @Test fun oversized_files_fail() = assertFalse(FontFileValidator.isUsableFont(data(byteArrayOf(0, 1, 0, 0), FontFileValidator.MAX_BYTES + 1)))
}
