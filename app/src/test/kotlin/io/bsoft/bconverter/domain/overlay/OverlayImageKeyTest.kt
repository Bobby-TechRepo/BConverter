package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OverlayImageKeyTest {
    private val base = TextOverlay("a", "", 0, 3000, image = OverlayImage("/p/logo.png", 400, 200))
    private fun key(o: TextOverlay = base, stamp: Long = 1, w: Int = 1920, h: Int = 1080) =
        OverlayLayerKey.of(OverlayRasterRequest(o, w, h, null, 0, imageStamp = stamp))

    @Test fun same_picture_same_key() { assertEquals(key(), key()) }
    @Test fun position_time_and_animation_do_not_change_it() {
        assertEquals(key(), key(base.copy(id = "b", startMs = 10, endMs = 99, centerX = 0.2f, centerY = 0.8f, animIn = OverlayAnimation(AnimationKind.FADE))))
    }
    @Test fun the_file_and_its_stamp_change_it() {
        assertNotEquals(key(), key(base.copy(image = base.image!!.copy(path = "/p/other.png"))))
        assertNotEquals(key(), key(stamp = 2))
    }
    @Test fun size_rotation_and_opacity_change_it() {
        assertNotEquals(key(), key(base.copy(scale = 2f)))
        assertNotEquals(key(), key(base.copy(rotationDeg = 15f)))
        assertNotEquals(key(), key(base.copy(style = base.style.copy(opacity = 0.5f))))
        assertNotEquals(key(), key(base.copy(image = base.image!!.copy(heightFraction = 0.5f))))
    }
    @Test fun frame_size_changes_it() { assertNotEquals(key(), key(w = 1280, h = 720)) }
    @Test fun a_picture_and_a_text_never_share_a_key() {
        assertNotEquals(key(), OverlayLayerKey.of(OverlayRasterRequest(TextOverlay("a", "", 0, 3000), 1920, 1080, null, 0)))
    }
}
