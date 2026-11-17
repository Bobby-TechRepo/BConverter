package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropRect
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerTransformTest {
    private fun stored(vararg ts: ClipTransform): StoredProject {
        val clips = ts.mapIndexed { i, t -> TL.video("c$i", 5_000).copy(transform = t) }
        return StoredProject(VideoProject("p", "P", TL.timeline(*clips.toTypedArray()), TL.exportSettings()), 1, 2)
    }
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project

    @Test fun each_clip_keeps_its_own_transform() {
        val t = ClipTransform(CropRect(0.1f, 0f, 1f, 0.9f), 90, flipVertical = true)
        val p = decode(ProjectSerializer.encode(stored(t, ClipTransform.None)))
        assertEquals(listOf(t, ClipTransform.None), p.timeline.videoClips.map { it.transform })
    }
    @Test fun untouched_clips_write_no_transform() {
        assertFalse(ProjectSerializer.encode(stored(ClipTransform.None)).contains("\"transform\""))
    }
}
