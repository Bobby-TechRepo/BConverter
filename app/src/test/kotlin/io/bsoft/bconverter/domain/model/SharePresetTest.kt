package io.bsoft.bconverter.domain.model

import io.bsoft.bconverter.domain.canvas.CanvasAspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePresetTest {
    @Test fun every_preset_has_a_shape_a_size_and_a_quality() {
        for (p in SharePreset.entries) { assertTrue(p.name, p.aspect != CanvasAspect.AUTO); assertTrue(p.name, p.size != ExportSize.AUTO) }
    }
    @Test fun the_shapes_match_the_places() {
        assertEquals(CanvasAspect.WIDE, SharePreset.YOUTUBE.aspect)
        assertEquals(CanvasAspect.TALL, SharePreset.VERTICAL_SHORTS.aspect)
        assertEquals(CanvasAspect.PORTRAIT, SharePreset.INSTAGRAM_POST.aspect)
        assertEquals(CanvasAspect.SQUARE, SharePreset.SQUARE.aspect)
    }
    @Test fun whatsapp_status_is_small() {
        assertEquals(ExportSize.P720, SharePreset.WHATSAPP_STATUS.size); assertEquals(ExportPreset.SMALL_FILE, SharePreset.WHATSAPP_STATUS.quality)
    }
    @Test fun a_1080p_vertical_video_from_a_landscape_clip_is_1080_by_1920() {
        val shaped = io.bsoft.bconverter.domain.canvas.CanvasSettings(SharePreset.VERTICAL_SHORTS.aspect).sizeFor(1920, 1080)
        assertEquals(1080 to 1920, shaped)
        assertEquals(null, SharePreset.VERTICAL_SHORTS.size.cap(Resolution(shaped.first, shaped.second)))
    }
    @Test fun a_4k_clip_is_brought_down_to_1080_on_the_short_side() {
        val shaped = io.bsoft.bconverter.domain.canvas.CanvasSettings(SharePreset.YOUTUBE.aspect).sizeFor(3840, 2160)
        assertEquals(Resolution(1920, 1080), SharePreset.YOUTUBE.size.cap(Resolution(shaped.first, shaped.second)))
    }

    @Test fun the_choices_that_make_up_a_preset_are_recognised() {
        for (p in SharePreset.entries) assertEquals(p, SharePreset.matching(p.aspect, p.size, p.quality))
    }
    @Test fun changing_any_one_choice_unselects_the_preset() {
        val p = SharePreset.YOUTUBE
        assertEquals(null, SharePreset.matching(CanvasAspect.AUTO, p.size, p.quality))
        assertEquals(null, SharePreset.matching(p.aspect, ExportSize.P720, p.quality))
        assertEquals(null, SharePreset.matching(p.aspect, p.size, ExportPreset.HIGH_QUALITY))
        assertEquals(null, SharePreset.matching(CanvasAspect.AUTO, ExportSize.AUTO, ExportPreset.BALANCED))
    }
}
