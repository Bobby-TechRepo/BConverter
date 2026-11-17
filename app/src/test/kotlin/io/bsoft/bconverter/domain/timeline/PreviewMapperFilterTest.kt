package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewMapperFilterTest {
    @Test fun a_segment_carries_its_clips_filter() {
        val warm = ClipFilter(temperature = 0.4f)
        val t = TL.timeline(TL.video("a", 5_000).copy(filter = warm), TL.video("b", 5_000))
        assertEquals(listOf(warm, ClipFilter.None), PreviewMapper.segments(t).map { it.filter })
    }
}
