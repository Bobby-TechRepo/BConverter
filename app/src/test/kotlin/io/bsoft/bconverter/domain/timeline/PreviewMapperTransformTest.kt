package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewMapperTransformTest {
    @Test fun a_segment_carries_its_clips_transform() {
        val t = ClipTransform(rotationDegrees = 270)
        val tl = TL.timeline(TL.video("a", 5_000).copy(transform = t), TL.video("b", 5_000))
        assertEquals(listOf(t, ClipTransform.None), PreviewMapper.segments(tl).map { it.transform })
    }
}
