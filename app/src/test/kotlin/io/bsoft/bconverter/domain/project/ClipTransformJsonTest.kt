package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.transform.CropRect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipTransformJsonTest {
    @Test fun the_identity_writes_nothing() { assertEquals(0, ClipTransformJson.encode(ClipTransform.None).length()) }
    @Test fun everything_round_trips() {
        val t = ClipTransform(CropRect(0.1f, 0.2f, 0.9f, 0.8f), 270, flipHorizontal = true, flipVertical = true)
        assertEquals(t, ClipTransformJson.decode(ClipTransformJson.encode(t)))
    }
    @Test fun only_changes_are_written() {
        assertEquals(setOf("rotation"), ClipTransformJson.encode(ClipTransform(rotationDegrees = 90)).keys().asSequence().toSet())
    }
    @Test fun an_empty_object_is_the_identity() { assertEquals(ClipTransform.None, ClipTransformJson.decode(JSONObject())) }
    @Test fun a_crop_out_of_range_is_clamped() {
        val c = ClipTransformJson.decode(JSONObject().put("crop", JSONObject().put("l", -3.0).put("r", 7.0))).crop
        assertEquals(CropRect.Full, c)
    }
    @Test fun a_crop_that_is_too_small_means_no_crop() {
        assertEquals(CropRect.Full, ClipTransformJson.decode(JSONObject().put("crop", JSONObject().put("l", 0.5).put("r", 0.5))).crop)
    }
    @Test fun a_strange_rotation_snaps_to_a_quarter_turn() {
        assertEquals(90, ClipTransformJson.decode(JSONObject().put("rotation", 100)).rotationDegrees)
        assertEquals(270, ClipTransformJson.decode(JSONObject().put("rotation", -90)).rotationDegrees)
        assertEquals(0, ClipTransformJson.decode(JSONObject().put("rotation", 360)).rotationDegrees)
    }
}
