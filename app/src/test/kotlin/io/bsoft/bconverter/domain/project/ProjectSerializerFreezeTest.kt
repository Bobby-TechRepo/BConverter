package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.TimelineOperations
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerFreezeTest {
    private fun stored(t: io.bsoft.bconverter.domain.timeline.Timeline) = StoredProject(VideoProject("p", "P", t, TL.exportSettings()), 1, 2)
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project
    private val frozen = ok(TimelineOperations.insertFreeze(TL.timeline(TL.video("a", 10_000)), "a", 4_000, "f", "a2", 3_000))

    @Test fun a_freeze_frame_survives_save_and_load() {
        val c = decode(ProjectSerializer.encode(stored(frozen))).timeline.videoClips
        assertEquals(listOf(0L, 3_000L, 0L), c.map { it.freezeMs }); assertEquals(13_000L, decode(ProjectSerializer.encode(stored(frozen))).timeline.videoDurationMs)
    }
    @Test fun ordinary_projects_write_no_freeze_key() { assertFalse(ProjectSerializer.encode(stored(TL.timeline(TL.video("a", 5_000)))).contains("freezeMs")) }
    @Test fun an_out_of_range_length_loads_as_an_ordinary_clip() {
        val text = ProjectSerializer.encode(stored(frozen)).replace("\"freezeMs\":3000", "\"freezeMs\":99999999")
        assertEquals(listOf(0L, 0L, 0L), decode(text).timeline.videoClips.map { it.freezeMs })
    }
}
