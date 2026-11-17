package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.canvas.CanvasAspect
import io.bsoft.bconverter.domain.canvas.CanvasBackground
import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerCanvasTest {
    private fun stored(c: CanvasSettings) = StoredProject(VideoProject("p", "P", TL.timeline(TL.video("a", 10_000)), TL.exportSettings(), canvas = c), 1, 2)
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project

    @Test fun a_colour_canvas_survives_save_and_load() {
        val c = CanvasSettings(CanvasAspect.PORTRAIT, CanvasFit.FILL, CanvasBackground.Color(0x336699))
        assertEquals(c, decode(ProjectSerializer.encode(stored(c))).canvas)
    }
    @Test fun a_blur_canvas_survives_save_and_load() {
        val c = CanvasSettings(CanvasAspect.TALL, CanvasFit.FIT, CanvasBackground.Blur)
        assertEquals(c, decode(ProjectSerializer.encode(stored(c))).canvas)
    }
    @Test fun the_default_is_not_written_and_old_projects_open_with_it() {
        val text = ProjectSerializer.encode(stored(CanvasSettings()))
        assertFalse(JSONObject(text).has("canvas")); assertEquals(CanvasSettings(), decode(text).canvas)
    }
    @Test fun unknown_or_bad_values_fall_back_one_by_one() {
        val o = JSONObject(ProjectSerializer.encode(stored(CanvasSettings(CanvasAspect.TALL, CanvasFit.FILL, CanvasBackground.Blur))))
        o.put("canvas", JSONObject().put("aspect", "HEXAGON").put("fit", "FILL").put("background", 99999999))
        val c = decode(o.toString()).canvas
        assertEquals(CanvasAspect.AUTO, c.aspect); assertEquals(CanvasFit.FILL, c.fit); assertEquals(CanvasBackground.Color(0xFFFFFF), c.background)
    }
}
