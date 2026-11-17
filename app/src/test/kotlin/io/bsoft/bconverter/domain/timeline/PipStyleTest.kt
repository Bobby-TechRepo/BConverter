package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipStyleTest {
    private fun clip(fin: Long = 0, fout: Long = 0) = PipClip("p", TL.source("p"), 9_000, 0, 4_000, 2_000, fadeInMs = fin, fadeOutMs = fout)
    private val withPip = ok(TimelineOperations.addPip(TL.timeline(TL.video("a", 10_000)), clip()))

    // ---- fade factor ----
    @Test fun without_fades_the_clip_is_fully_there() { assertEquals(1f, PipFade.factor(clip(), 2_000), 0f); assertEquals(1f, PipFade.factor(clip(), 5_999), 0f) }
    @Test fun a_fade_in_rises_over_its_length() {
        val c = clip(fin = 1_000)
        assertEquals(0f, PipFade.factor(c, 2_000), 0.001f); assertEquals(0.5f, PipFade.factor(c, 2_500), 0.001f); assertEquals(1f, PipFade.factor(c, 3_000), 0.001f)
    }
    @Test fun a_fade_out_falls_to_nothing_at_the_end() {
        val c = clip(fout = 1_000)
        assertEquals(1f, PipFade.factor(c, 5_000), 0.001f); assertEquals(0.5f, PipFade.factor(c, 5_500), 0.001f); assertEquals(0f, PipFade.factor(c, 6_000), 0.001f)
    }
    @Test fun fades_that_do_not_fit_are_shortened_with_the_fade_in_first() {
        assertEquals(3_000L to 1_000L, PipFade.fitted(clip(fin = 3_000, fout = 3_000)))
        assertEquals(4_000L to 0L, PipFade.fitted(clip(fin = 9_000, fout = 2_000)))
    }
    @Test fun the_factor_stays_between_zero_and_one_outside_the_clip() {
        val c = clip(fin = 1_000, fout = 1_000)
        assertEquals(0f, PipFade.factor(c, 0), 0f); assertEquals(0f, PipFade.factor(c, 9_000), 0f)
    }

    // ---- model ----
    @Test fun a_fade_longer_than_ten_seconds_is_not_a_clip() { assertTrue(runCatching { clip(fin = 10_001) }.isFailure); assertTrue(runCatching { clip(fout = -1) }.isFailure) }
    @Test fun a_border_must_be_thin_and_a_colour() {
        assertTrue(runCatching { PipLayout(borderWidth = 0.11f) }.isFailure); assertTrue(runCatching { PipLayout(borderWidth = -0.1f) }.isFailure)
        assertTrue(runCatching { PipLayout(borderRgb = 0x1000000) }.isFailure); PipLayout(borderWidth = 0.1f, borderRgb = 0xFFFFFF)
    }
    @Test fun the_default_has_no_border_and_no_fades() { assertEquals(0f, PipLayout().borderWidth, 0f); assertEquals(0L, clip().fadeInMs) }

    // ---- operation ----
    @Test fun fades_can_be_set() { val c = ok(TimelineOperations.setPipFades(withPip, "p", 500, 1_500)).pipClips.single(); assertEquals(500L, c.fadeInMs); assertEquals(1_500L, c.fadeOutMs) }
    @Test fun a_bad_fade_is_refused() {
        assertEquals(EditError.InvalidFade, err(TimelineOperations.setPipFades(withPip, "p", -1, 0)))
        assertEquals(EditError.InvalidFade, err(TimelineOperations.setPipFades(withPip, "p", 0, 10_001)))
    }
    @Test fun fades_of_an_unknown_clip_are_refused() { assertTrue(TimelineOperations.setPipFades(withPip, "zz", 0, 0) is EditResult.Failure) }
    @Test fun a_trim_keeps_the_fades() {
        val faded = ok(TimelineOperations.setPipFades(withPip, "p", 500, 500))
        assertEquals(500L, ok(TimelineOperations.trimPip(faded, "p", 0, 2_000)).pipClips.single().fadeInMs)
    }
}
