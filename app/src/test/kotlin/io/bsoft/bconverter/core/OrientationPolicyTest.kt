package io.bsoft.bconverter.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPolicyTest {
    @Test fun phone_is_portrait_only() =
        assertEquals(ScreenOrientation.PORTRAIT, OrientationPolicy.forSmallestWidthDp(360))

    @Test fun just_below_tablet_threshold_is_portrait() =
        assertEquals(ScreenOrientation.PORTRAIT, OrientationPolicy.forSmallestWidthDp(599))

    @Test fun tablet_threshold_is_landscape_only() =
        assertEquals(ScreenOrientation.LANDSCAPE, OrientationPolicy.forSmallestWidthDp(600))

    @Test fun large_tablet_is_landscape_only() =
        assertEquals(ScreenOrientation.LANDSCAPE, OrientationPolicy.forSmallestWidthDp(840))
}
