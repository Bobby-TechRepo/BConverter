package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.domain.filter.FilterPresets
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerFilterTest {
    private val vivid = FilterPresets.byId("vivid")!!.filter
    private fun stored(vararg filters: ClipFilter): StoredProject {
        val clips = filters.mapIndexed { i, f -> TL.video("c$i", 5_000).copy(filter = f) }
        return StoredProject(VideoProject("p", "P", TL.timeline(*clips.toTypedArray()), TL.exportSettings()), 1, 2)
    }
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project

    @Test fun each_clip_keeps_its_own_filter() {
        val p = decode(ProjectSerializer.encode(stored(vivid, ClipFilter.None, ClipFilter(hue = 0.2f))))
        assertEquals(listOf(vivid, ClipFilter.None, ClipFilter(hue = 0.2f)), p.timeline.videoClips.map { it.filter })
    }
    @Test fun an_unfiltered_project_writes_no_filter_key() {
        assertFalse(ProjectSerializer.encode(stored(ClipFilter.None)).contains("\"filter\""))
    }
    @Test fun a_project_saved_before_filters_existed_opens_unfiltered() {
        val old = ProjectSerializer.encode(stored(ClipFilter.None))
        assertTrue(decode(old).timeline.videoClips.all { it.filter.isIdentity })
    }
}
