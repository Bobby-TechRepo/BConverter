package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportSizeTest {
    @Test fun auto_and_original_keep_the_size() {
        assertNull(ExportSize.AUTO.cap(Resolution(1920, 1080)))
        assertNull(ExportSize.ORIGINAL.cap(Resolution(1920, 1080)))
    }

    @Test fun landscape_is_capped_on_the_shorter_side() =
        assertEquals(Resolution(1280, 720), ExportSize.P720.cap(Resolution(1920, 1080)))

    @Test fun portrait_is_capped_on_the_shorter_side() =
        assertEquals(Resolution(480, 854), ExportSize.P480.cap(Resolution(1080, 1920)))

    @Test fun a_smaller_or_equal_project_is_never_enlarged() {
        assertNull(ExportSize.P1080.cap(Resolution(1280, 720)))
        assertNull(ExportSize.P720.cap(Resolution(1280, 720)))
    }

    @Test fun unknown_size_is_left_alone() = assertNull(ExportSize.P480.cap(null))

    @Test fun results_are_even() {
        val r = ExportSize.P480.cap(Resolution(1001, 777))!!
        assertEquals(0, r.width % 2); assertEquals(0, r.height % 2)
    }
}
