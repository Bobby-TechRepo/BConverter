package io.bsoft.bconverter.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material window size classes (breakpoints from the Material 3 adaptive guidance). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }
enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * The size of the app's window, not of the device (step 21). Layout decisions follow the window, so split screen,
 * freeform windows, a folded or unfolded foldable and a rotated tablet each get the layout that fits the space they
 * actually have. The device's smallest width still decides the orientation policy ([OrientationPolicy]), which is a
 * property of the device, not the window.
 */
@Immutable
data class WindowLayout(val widthDp: Int, val heightDp: Int) {
    val widthClass: WidthClass = when {
        widthDp < MEDIUM_MIN_WIDTH_DP -> WidthClass.COMPACT
        widthDp < EXPANDED_MIN_WIDTH_DP -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
    val heightClass: HeightClass = when {
        heightDp < MEDIUM_MIN_HEIGHT_DP -> HeightClass.COMPACT
        heightDp < EXPANDED_MIN_HEIGHT_DP -> HeightClass.MEDIUM
        else -> HeightClass.EXPANDED
    }

    /**
     * Room for two panes side by side: any expanded width, or a medium width held in landscape (a tablet in split
     * screen held upright stays one pane; an unfolded foldable on its side gets two).
     */
    val twoPane: Boolean get() = widthClass == WidthClass.EXPANDED || (widthClass == WidthClass.MEDIUM && widthDp > heightDp)

    /** How wide a centred column of lists may grow: one comfortable reading width, or wider when there are two panes. */
    val contentMaxWidth: Dp get() = if (twoPane) WIDE_CONTENT_MAX_DP.dp else READABLE_MAX_WIDTH_DP.dp

    companion object {
        const val MEDIUM_MIN_WIDTH_DP = 600
        const val EXPANDED_MIN_WIDTH_DP = 840
        const val MEDIUM_MIN_HEIGHT_DP = 480
        const val EXPANDED_MIN_HEIGHT_DP = 900
        const val WIDE_CONTENT_MAX_DP = 1120

        /** A typical phone held upright; the default when nothing is provided (previews, tests). */
        val Phone = WindowLayout(411, 891)
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Phone }

/**
 * Provides the current window's [WindowLayout]. The configuration's screen size is this window's size (it follows
 * multi-window resizes and folding), and a resize recomposes from here.
 */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    CompositionLocalProvider(LocalWindowLayout provides WindowLayout(configuration.screenWidthDp, configuration.screenHeightDp), content = content)
}
