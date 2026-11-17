package io.bsoft.bconverter.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTimeInputTest {
    @Test fun formats_as_minutes_seconds_and_millis() {
        assertEquals("0:00.000", SubtitleTimeInput.format(0)); assertEquals("0:01.500", SubtitleTimeInput.format(1500))
        assertEquals("12:34.056", SubtitleTimeInput.format(754_056)); assertEquals("1:02:03.004", SubtitleTimeInput.format(3_723_004))
    }

    @Test fun parses_what_it_formats() {
        listOf(0L, 1500L, 754_056L, 3_723_004L, 59_999L).forEach { assertEquals(it, SubtitleTimeInput.parse(SubtitleTimeInput.format(it))) }
    }

    @Test fun parses_plain_seconds_and_short_forms() {
        assertEquals(1500L, SubtitleTimeInput.parse("1.5")); assertEquals(2000L, SubtitleTimeInput.parse("2"))
        assertEquals(65_250L, SubtitleTimeInput.parse("1:05.25")); assertEquals(3_600_000L, SubtitleTimeInput.parse("1:00:00"))
        assertEquals(1500L, SubtitleTimeInput.parse(" 0:01,5 "))
    }

    @Test fun rejects_nonsense() {
        listOf("", "abc", "1:2:3:4", "-1", "1:75", "1.2345", ":").forEach { assertNull(it, SubtitleTimeInput.parse(it)) }
    }

    @Test fun signed_offsets_accept_a_leading_sign() {
        assertEquals(-1500L, SubtitleTimeInput.parseSigned("-1.5")); assertEquals(2000L, SubtitleTimeInput.parseSigned("+2")); assertEquals(500L, SubtitleTimeInput.parseSigned("0.5"))
        assertNull(SubtitleTimeInput.parseSigned("--1"))
        assertEquals("-0:01.500", SubtitleTimeInput.formatSigned(-1500)); assertEquals("0:02.000", SubtitleTimeInput.formatSigned(2000))
    }
}
