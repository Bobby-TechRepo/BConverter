package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.TextOverlay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextOverlayJsonImageTest {
    private val pic = TextOverlay(
        "p", "", 100, 4000, 0.3f, 0.7f, rotationDeg = 12f, scale = 1.5f,
        image = OverlayImage("/data/overlay-images/a.png", 640, 480, heightFraction = 0.4f),
        animIn = OverlayAnimation(AnimationKind.SCALE),
    ).let { it.copy(style = it.style.copy(opacity = 0.6f)) }

    @Test fun keyframes_survive_save_and_load_and_bad_ones_are_dropped() {
        val keys = listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.2f, 0.3f, 1f, 1f, eased = true), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(900, 0.8f, 0.7f, 1.5f, 0.5f))
        val o = TextOverlay("k", "Hi", 0, 3_000, keyframes = keys)
        assertEquals(keys, TextOverlayJson.decode(TextOverlayJson.encode(o)).keyframes)
        val bad = TextOverlayJson.encode(o).also { j -> j.getJSONArray("keys").getJSONObject(1).put("z", 9.0) }
        assertEquals(1, TextOverlayJson.decode(bad).keyframes.size)
        assertFalse(TextOverlayJson.encode(TextOverlay("p", "x", 0, 10)).has("keys"))
    }
    @Test fun a_keyframe_turn_survives_save_and_load_and_zero_is_not_written() {
        val keys = listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.2f, 0.3f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(900, 0.8f, 0.7f, 1f, 1f, false, -90f))
        val j = TextOverlayJson.encode(TextOverlay("k", "Hi", 0, 3_000, keyframes = keys))
        assertEquals(keys, TextOverlayJson.decode(j).keyframes)
        assertFalse(j.getJSONArray("keys").getJSONObject(0).has("r"))
        j.getJSONArray("keys").getJSONObject(1).put("r", 9_000.0)
        assertEquals(1, TextOverlayJson.decode(j).keyframes.size)
    }
    @Test fun a_picture_round_trips() { assertEquals(pic, TextOverlayJson.decode(TextOverlayJson.encode(pic))) }
    @Test fun a_text_overlay_writes_no_image_key() { assertFalse(TextOverlayJson.encode(TextOverlay("t", "Hi", 0, 1000)).has("image")) }
    @Test fun an_old_overlay_without_an_image_opens_as_text() { assertNull(TextOverlayJson.decode(JSONObject(TextOverlayJson.encode(TextOverlay("t", "Hi", 0, 1000)).toString())).image) }
    @Test fun the_default_height_is_not_written() {
        val o = TextOverlayJson.encode(pic.copy(image = OverlayImage("/a.png", 10, 10))).getJSONObject("image")
        assertFalse(o.has("height"))
    }
}
