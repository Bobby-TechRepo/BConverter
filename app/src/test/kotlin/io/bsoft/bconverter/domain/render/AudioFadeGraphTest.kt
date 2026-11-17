package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioFadeGraphTest {
    private fun plan(vararg clips: io.bsoft.bconverter.domain.timeline.VideoClip, audio: List<io.bsoft.bconverter.domain.timeline.AudioClip> = emptyList()) =
        (RenderPlanner.plan(TL.project(TL.withAudio(TL.timeline(*clips), audio))) as RenderPlanResult.Success).plan
    private fun origChain(p: RenderPlan) = FilterGraphBuilder.build(p).chains.first { it.outputLabels == listOf("a0") }.filters

    @Test fun the_plan_carries_volume_and_fades() {
        val p = plan(TL.video("a", 10_000).copy(volume = 0.5f, fadeInMs = 1_000, fadeOutMs = 2_000))
        val s = p.originalAudio!!.single()
        assertEquals(0.5f, s.volume, 0f); assertEquals(1_000L, s.fadeInMs); assertEquals(2_000L, s.fadeOutMs)
    }
    @Test fun a_video_clips_sound_gets_volume_then_fades_after_it_is_cut_to_length() {
        val f = origChain(plan(TL.video("a", 10_000).copy(volume = 0.5f, fadeInMs = 1_000, fadeOutMs = 2_000)))
        val i = f.indexOfFirst { it.startsWith("atrim") }
        assertEquals(listOf("volume=0.5", "afade=t=in:st=0:d=1", "afade=t=out:st=8:d=2"), f.drop(i + 1))
    }
    @Test fun an_untouched_clip_adds_nothing() {
        val f = origChain(plan(TL.video("a", 10_000)))
        assertTrue(f.none { it.startsWith("afade") || it.startsWith("volume") })
    }
    @Test fun a_fade_longer_than_the_clip_is_cut_to_fit() {
        val f = origChain(plan(TL.video("a", 3_000).copy(fadeInMs = 5_000, fadeOutMs = 5_000)))
        assertEquals(listOf("afade=t=in:st=0:d=3"), f.filter { it.startsWith("afade") })
    }
    @Test fun an_audio_clip_fades_before_it_is_delayed() {
        val a = TL.audio("m").copy(startTimeMs = 2_000, volume = 1.5f, fadeInMs = 500, fadeOutMs = 1_000)
        val p = plan(TL.video("a", 10_000), audio = listOf(a))
        val chain = FilterGraphBuilder.build(p).chains.first { it.outputLabels == listOf("o0") }.filters
        val vol = chain.indexOf("volume=1.5"); val inn = chain.indexOfFirst { it.startsWith("afade=t=in") }
        val delay = chain.indexOfFirst { it.startsWith("adelay") }
        assertTrue("$chain", vol in 0 until inn && inn < delay)
        assertTrue(chain.any { it == "afade=t=out:st=${RenderNumbers.seconds(a.durationMs - 1_000)}:d=1" })
    }
}
