package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.domain.transform.KenBurns
import io.bsoft.bconverter.domain.transform.KenBurnsMode
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerKenBurnsTest {
    private fun stored(vararg ks: KenBurns): StoredProject {
        val clips = ks.mapIndexed { i, k -> TL.video("c$i", 5_000).copy(kenBurns = k) }
        return StoredProject(VideoProject("p", "P", TL.timeline(*clips.toTypedArray()), TL.exportSettings()), 1, 2)
    }
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project

    @Test fun each_clip_keeps_its_own_motion() {
        val k = KenBurns(KenBurnsMode.PAN_RIGHT, 1.7f)
        assertEquals(listOf(k, KenBurns.None), decode(ProjectSerializer.encode(stored(k, KenBurns.None))).timeline.videoClips.map { it.kenBurns })
    }
    @Test fun clips_without_motion_write_nothing() = assertFalse(ProjectSerializer.encode(stored(KenBurns.None)).contains("kenBurns"))

    @Test fun a_bad_mode_means_no_motion_and_a_bad_amount_is_clamped() {
        val text = ProjectSerializer.encode(stored(KenBurns(KenBurnsMode.ZOOM_IN, 1.5f)))
        assertEquals(true, decode(text.replace("ZOOM_IN", "SIDEWAYS")).timeline.videoClips[0].kenBurns.isNone)
        assertEquals(2f, decode(text.replace("1.5", "9")).timeline.videoClips[0].kenBurns.amount)
    }
}
