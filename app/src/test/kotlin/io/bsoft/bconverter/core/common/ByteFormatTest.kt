package io.bsoft.bconverter.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test fun small_sizes_are_in_bytes() { assertEquals("0 B", ByteFormat.format(0)); assertEquals("1023 B", ByteFormat.format(1023)) }
    @Test fun larger_sizes_use_one_decimal_and_binary_steps() {
        assertEquals("1.0 KB", ByteFormat.format(1024)); assertEquals("1.5 KB", ByteFormat.format(1536))
        assertEquals("10.0 MB", ByteFormat.format(10L * 1024 * 1024)); assertEquals("1.5 GB", ByteFormat.format(1_610_612_736L))
    }
    @Test fun negative_sizes_read_as_zero() { assertEquals("0 B", ByteFormat.format(-5)) }
    @Test fun the_decimal_point_does_not_depend_on_the_device_language() {
        val old = java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY); assertEquals("1.5 KB", ByteFormat.format(1536)) } finally { java.util.Locale.setDefault(old) }
    }
}
