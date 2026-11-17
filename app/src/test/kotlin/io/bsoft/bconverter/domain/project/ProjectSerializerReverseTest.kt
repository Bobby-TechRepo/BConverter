package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerReverseTest {
    private fun stored(vararg reversed: Boolean) = StoredProject(
        VideoProject("p", "P", TL.timeline(*reversed.mapIndexed { i, r -> TL.video("c$i", 5_000).copy(reversed = r) }.toTypedArray()), TL.exportSettings()), 1, 2,
    )
    @Test fun a_reversed_clip_stays_reversed() {
        val p = (ProjectSerializer.decode(ProjectSerializer.encode(stored(true, false))) as DecodeResult.Decoded).stored.project
        assertEquals(listOf(true, false), p.timeline.videoClips.map { it.reversed })
    }
    @Test fun forward_clips_write_nothing_new() { assertFalse(ProjectSerializer.encode(stored(false)).contains("reversed")) }
}
