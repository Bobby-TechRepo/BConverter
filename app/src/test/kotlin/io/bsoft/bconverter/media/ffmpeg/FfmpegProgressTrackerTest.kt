package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.media.api.MediaProcessingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegProgressTrackerTest {
    private fun tracker(total: Long? = 10_000) = FfmpegProgressTracker(total)

    @Test fun a_progress_block_emits_when_it_ends() {
        val t = tracker()
        assertNull(t.accept("frame=150")); assertNull(t.accept("out_time_us=2500000")); assertNull(t.accept("speed=2.0x"))
        assertEquals(MediaProcessingProgress(2500, 10_000, 0.25f, 2.0, 3750), t.accept("progress=continue"))
    }

    @Test fun out_time_ms_is_microseconds_despite_its_name() {
        val t = tracker(); t.accept("out_time_ms=3000000")
        assertEquals(3000L, t.accept("progress=continue")!!.processedDurationMs)
    }

    @Test fun not_available_and_negative_times_are_treated_as_zero() {
        listOf("out_time_us=N/A", "out_time_us=-9223372036854775807").forEach {
            val t = tracker(); t.accept(it); assertEquals(0L, t.accept("progress=continue")!!.processedDurationMs)
        }
    }

    @Test fun unknown_total_gives_no_percentage_or_eta() {
        val t = tracker(null); t.accept("out_time_us=2000000"); t.accept("speed=1.0x")
        assertEquals(MediaProcessingProgress(2000, null, null, 1.0, null), t.accept("progress=continue"))
    }

    @Test fun unknown_or_zero_speed_gives_no_eta() {
        listOf("speed=N/A", "speed=0.0x", "speed=0x").forEach {
            val t = tracker(); t.accept("out_time_us=1000000"); t.accept(it)
            val p = t.accept("progress=continue")!!; assertNull(it, p.speed); assertNull(it, p.estimatedRemainingMs)
        }
    }

    @Test fun progress_never_exceeds_the_total() {
        val t = tracker(); t.accept("out_time_us=12000000")
        assertEquals(MediaProcessingProgress(10_000, 10_000, 1.0f, null, 0), t.accept("progress=continue"))
    }

    @Test fun progress_end_reports_completion() {
        val t = tracker(); t.accept("out_time_us=9000000"); t.accept("speed=1.5x")
        assertEquals(MediaProcessingProgress(10_000, 10_000, 1.0f, 1.5, 0), t.accept("progress=end"))
    }

    @Test fun classic_stats_line_is_understood() {
        val line = "frame=  240 fps= 30 q=28.0 size=    1024kB time=00:00:08.00 bitrate=1048.6kbits/s speed=1.5x"
        assertEquals(MediaProcessingProgress(8000, 10_000, 0.8f, 1.5, 1333), tracker().accept(line))
    }

    @Test fun classic_line_with_hours_and_fraction() {
        assertEquals(3_723_500L, tracker(null).accept("size=1kB time=01:02:03.50 speed=1x")!!.processedDurationMs)
    }

    @Test fun key_value_time_lines_are_not_mistaken_for_stats_lines() = assertNull(tracker().accept("out_time=00:00:02.500000"))

    @Test fun garbage_and_blank_lines_are_ignored() { assertNull(tracker().accept("")); assertNull(tracker().accept("Press [q] to stop")) }

    // ---- statistics callback (FFmpegKit) ----
    @Test fun statistics_callback_values_become_progress() =
        assertEquals(MediaProcessingProgress(2500, 10_000, 0.25f, 2.0, 3750), tracker().onStatistics(2500, 2.0))

    @Test fun statistics_with_no_valid_speed_or_negative_time_are_sanitised() {
        assertEquals(MediaProcessingProgress(0, 10_000, 0.0f, null, null), tracker().onStatistics(-5, 0.0))
        assertNull(tracker().onStatistics(1000, -1.0).speed)
    }
}
