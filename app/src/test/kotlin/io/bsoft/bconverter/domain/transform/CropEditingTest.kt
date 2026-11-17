package io.bsoft.bconverter.domain.transform

import io.bsoft.bconverter.domain.transform.CropEditing.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropEditingTest {
    private val full = CropRect.Full

    @Test fun each_edge_cuts_from_its_own_side() {
        assertEquals(CropRect(left = 0.2f), CropEditing.withCut(full, CropEdge.LEFT, 0.2f))
        assertEquals(CropRect(top = 0.2f), CropEditing.withCut(full, CropEdge.TOP, 0.2f))
        assertEquals(0.8f, CropEditing.withCut(full, CropEdge.RIGHT, 0.2f).right, 1e-6f)
        assertEquals(0.8f, CropEditing.withCut(full, CropEdge.BOTTOM, 0.2f).bottom, 1e-6f)
    }
    @Test fun the_cut_reads_back() {
        for (e in CropEdge.entries) assertEquals(0.3f, CropEditing.cut(CropEditing.withCut(full, e, 0.3f), e), 1e-6f)
    }
    @Test fun an_edge_stops_before_it_meets_the_other() {
        val c = CropEditing.withCut(CropRect(right = 0.5f), CropEdge.LEFT, 0.9f)
        assertEquals(0.4f, c.left, 1e-6f); assertEquals(CropRect.MIN_SIDE, c.width, 1e-6f)
        val d = CropEditing.withCut(CropRect(left = 0.5f), CropEdge.RIGHT, 0.9f)
        assertEquals(0.6f, d.right, 1e-6f)
    }
    @Test fun a_negative_or_broken_amount_means_no_cut() {
        assertEquals(full, CropEditing.withCut(full, CropEdge.LEFT, -1f))
        assertEquals(full, CropEditing.withCut(full, CropEdge.TOP, Float.NaN))
    }
    @Test fun a_huge_cut_never_makes_an_invalid_crop() {
        for (e in CropEdge.entries) CropEditing.withCut(full, e, 5f)
    }
    @Test fun the_whole_shape_is_the_full_frame() {
        assertEquals(full, CropEditing.cropFor(Shape.WHOLE, ClipTransform.None, 1920, 1080))
    }
    @Test fun a_square_from_a_wide_picture() {
        val c = CropEditing.cropFor(Shape.SQUARE, ClipTransform.None, 1920, 1080)
        assertEquals(1080 to 1080, ClipTransform(crop = c).outputSize(1920, 1080))
    }
    @Test fun a_wanted_tall_shape_is_measured_on_the_picture_as_shot_when_it_is_turned() {
        val t = ClipTransform(rotationDegrees = 90)
        val c = CropEditing.cropFor(Shape.TALL, t, 1920, 1080)
        val (w, h) = t.withCrop(c).outputSize(1920, 1080)
        assertEquals(9f / 16f, w.toFloat() / h, 0.01f)
    }
    @Test fun an_unknown_source_size_keeps_the_whole_picture() {
        assertEquals(full, CropEditing.cropFor(Shape.SQUARE, ClipTransform.None, 0, 0))
    }
    @Test fun the_current_shape_is_recognised() {
        val t = ClipTransform(crop = CropEditing.cropFor(Shape.WIDE, ClipTransform.None, 1000, 1000))
        assertEquals(Shape.WIDE, CropEditing.shapeOf(t, 1000, 1000))
        assertEquals(Shape.WHOLE, CropEditing.shapeOf(ClipTransform.None, 1000, 1000))
        assertNull(CropEditing.shapeOf(ClipTransform(crop = CropRect(left = 0.123f)), 1000, 1000))
    }
}
