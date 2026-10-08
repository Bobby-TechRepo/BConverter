package io.bsoft.bconverter.core.ui

enum class ScreenOrientation { PORTRAIT, LANDSCAPE }

/** Spec §37: phones are portrait-only, tablets are landscape-only. */
object OrientationPolicy {
    const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

    fun forSmallestWidthDp(smallestWidthDp: Int): ScreenOrientation =
        if (smallestWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP) ScreenOrientation.LANDSCAPE
        else ScreenOrientation.PORTRAIT
}
