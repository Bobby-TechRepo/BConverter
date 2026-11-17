package io.bsoft.bconverter.media.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Test

class LogTailTest {
    @Test fun fragments_are_joined_in_order() {
        val t = LogTail(100); t.add("Input #0, mov"); t.add(", from 'a.mp4':\n"); t.add(null); t.add("")
        assertEquals("Input #0, mov, from 'a.mp4':\n", t.text())
    }

    @Test fun only_the_end_is_kept_starting_at_a_whole_line() {
        val t = LogTail(20)
        t.add("first line that is long\n"); t.add("second\n"); t.add("Conversion failed!\n")
        assertEquals("Conversion failed!\n", t.text())
    }

    @Test fun a_single_long_line_is_kept_as_its_last_characters() {
        val t = LogTail(5); t.add("abcdefghij")
        assertEquals("fghij", t.text())
    }
}
