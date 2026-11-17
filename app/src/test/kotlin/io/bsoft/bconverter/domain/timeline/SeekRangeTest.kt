package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekRangeTest {
    private val video = TL.timeline(TL.video("a", 10_000), TL.video("b", 5_000))

    @Test fun an_empty_timeline_cannot_be_sought() {
        assertEquals(0L, SeekRange.limitMs(Timeline()))
    }

    @Test fun the_range_runs_past_the_end_by_the_blank_tail() {
        assertEquals(15_000L + SeekRange.BLANK_TAIL_MS, SeekRange.limitMs(video))
    }

    @Test fun clamp_keeps_a_position_inside_the_range() {
        assertEquals(0L, SeekRange.clamp(video, -1))
        assertEquals(12_000L, SeekRange.clamp(video, 12_000))
        assertEquals(20_000L, SeekRange.clamp(video, 20_000))
        assertEquals(SeekRange.limitMs(video), SeekRange.clamp(video, 1_000_000))
        assertEquals(0L, SeekRange.clamp(Timeline(), 5_000))
    }

    @Test fun a_position_is_blank_only_after_the_last_video_frame() {
        assertFalse(SeekRange.isBlank(video, 0)); assertFalse(SeekRange.isBlank(video, 14_999)); assertFalse(SeekRange.isBlank(video, 15_000))
        assertTrue(SeekRange.isBlank(video, 15_001)); assertTrue(SeekRange.isBlank(video, 24_000))
    }

    @Test fun audio_that_outlasts_the_video_extends_the_range_but_the_picture_stays_blank_after_the_video() {
        val withMusic = video.copy(audioTracks = listOf(AudioTrack("t", listOf(TL.audio("m", 20_000, start = 4_000)))))
        assertEquals(24_000L + SeekRange.BLANK_TAIL_MS, SeekRange.limitMs(withMusic))
        assertTrue(SeekRange.isBlank(withMusic, 20_000))
    }
}
