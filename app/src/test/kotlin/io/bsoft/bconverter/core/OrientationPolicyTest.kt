package io.bsoft.bconverter.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationPolicyTest {
    @Test fun phone_is_portrait_only() =
        assertEquals(ScreenOrientation.PORTRAIT, OrientationPolicy.forSmallestWidthDp(360))

    @Test fun just_below_tablet_threshold_is_portrait() =
        assertEquals(ScreenOrientation.PORTRAIT, OrientationPolicy.forSmallestWidthDp(599))

    // Android 16 ignores orientation locks on large screens for apps targeting API 36, so tablets are never locked.
    @Test fun tablet_threshold_follows_the_device() =
        assertEquals(ScreenOrientation.FOLLOW_DEVICE, OrientationPolicy.forSmallestWidthDp(600))

    @Test fun large_tablet_follows_the_device() =
        assertEquals(ScreenOrientation.FOLLOW_DEVICE, OrientationPolicy.forSmallestWidthDp(840))

    @Test fun large_screen_threshold() {
        assertFalse(OrientationPolicy.isLargeScreen(599)); assertTrue(OrientationPolicy.isLargeScreen(600))
    }
}
