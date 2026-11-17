package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.filter.ClipFilter
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RenderPlannerFilterTest {
    @Test fun each_clips_filter_reaches_its_segment() {
        val a = TL.video("a", 10_000).copy(filter = ClipFilter(contrast = 0.2f))
        val b = TL.video("b", 10_000)
        val plan = (RenderPlanner.plan(TL.project(TL.timeline(a, b))) as RenderPlanResult.Success).plan
        assertEquals(ClipFilter(contrast = 0.2f), plan.videoSegments[0].filter)
        assertEquals(ClipFilter.None, plan.videoSegments[1].filter)
    }
}
