package io.bsoft.bconverter.core.ui

/** [FOLLOW_DEVICE] = no lock: the window follows the device and the user's rotation setting. */
enum class ScreenOrientation { PORTRAIT, FOLLOW_DEVICE }

/**
 * Phones stay portrait. Large screens (smallest width ≥ 600 dp) are not locked: apps targeting API 36 have
 * orientation locks ignored there by Android 16, and API 37 removes the opt-out, so tablet and foldable layouts must
 * work in both orientations (spec §37, revised for Android 16).
 */
object OrientationPolicy {
    const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

    fun isLargeScreen(smallestWidthDp: Int): Boolean = smallestWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP

    fun forSmallestWidthDp(smallestWidthDp: Int): ScreenOrientation =
        if (isLargeScreen(smallestWidthDp)) ScreenOrientation.FOLLOW_DEVICE else ScreenOrientation.PORTRAIT
}
