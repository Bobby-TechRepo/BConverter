package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropRect
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RenderPlannerTransformTest {
    private fun plan(vararg clips: io.bsoft.bconverter.domain.timeline.VideoClip) =
        (RenderPlanner.plan(TL.project(TL.timeline(*clips))) as RenderPlanResult.Success).plan

    @Test fun each_clips_transform_and_source_size_reach_its_segment() {
        val t = ClipTransform(rotationDegrees = 90)
        val p = plan(TL.video("a", 10_000).copy(transform = t), TL.video("b", 10_000))
        assertEquals(t, p.videoSegments[0].transform); assertEquals(ClipTransform.None, p.videoSegments[1].transform)
        assertEquals(1920, p.videoSegments[0].sourceWidth); assertEquals(1080, p.videoSegments[0].sourceHeight)
    }
    @Test fun the_canvas_follows_the_first_clip_after_its_transform() {
        assertEquals(1080, plan(TL.video("a", 10_000).copy(transform = ClipTransform(rotationDegrees = 90))).canvas.width)
        assertEquals(1920, plan(TL.video("a", 10_000).copy(transform = ClipTransform(rotationDegrees = 90))).canvas.height)
    }
    @Test fun a_crop_shrinks_the_canvas() {
        val c = plan(TL.video("a", 10_000).copy(transform = ClipTransform(crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f)))).canvas
        assertEquals(960, c.width); assertEquals(540, c.height)
    }
    @Test fun a_later_clips_transform_does_not_change_the_canvas() {
        val c = plan(TL.video("a", 10_000), TL.video("b", 10_000).copy(transform = ClipTransform(rotationDegrees = 90))).canvas
        assertEquals(1920, c.width); assertEquals(1080, c.height)
    }
    @Test fun the_graph_crops_and_turns_before_scaling() {
        val p = plan(TL.video("a", 10_000).copy(transform = ClipTransform(CropRect(0f, 0f, 0.5f, 1f), 90)))
        val chain = FilterGraphBuilder.build(p).chains.first().filters
        assertEquals(listOf("crop=960:1080:0:0", "transpose=1"), chain.take(2))
        assertTrue(chain[2].startsWith("scale="))
    }
}
