package io.bsoft.bconverter.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bsoft.bconverter.R

/*
 * BConverter "Studio" design system.
 *
 * Identity: a deep graphite canvas with an iris → violet → rose signature gradient. The gradient is reserved for the one
 * thing that matters on a screen (the main action, the hero, the Export pill) so it keeps its weight. Everything else is
 * quiet tonal surfaces, generous radii and a single geometric typeface (Plus Jakarta Sans, OFL, bundled in res/font).
 *
 * Text pairs pass WCAG 4.5:1 in both palettes (dark 6.3–16.7, light 5.3–17.2; outlines ≥ 3:1 on the surface). White on
 * every gradient stop is ≥ 4.8:1. Re-run the check in docs/ACCESSIBILITY_PERFORMANCE.md after changing a value.
 */

// ---------------------------------------------------------------- palette

private object Palette {
    // Signature gradient stops (white text passes 4.5:1 on each).
    val Iris = Color(0xFF6A4CFF)
    val Violet = Color(0xFF8E44E6)
    val Rose = Color(0xFFC93A6E)

    val Success = Color(0xFF3DDC97)
    val SuccessDeep = Color(0xFF0B7A50)
}

/** Brand palette used wherever dynamic colour is off (the default), and always in the editor. */
internal val BrandDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA99CFF), onPrimary = Color(0xFF1B1060),
    primaryContainer = Color(0xFF3A2E9C), onPrimaryContainer = Color(0xFFE8E3FF),
    inversePrimary = Color(0xFF5B44F2),
    secondary = Color(0xFFFF8FAD), onSecondary = Color(0xFF5A0A28),
    secondaryContainer = Color(0xFF6B1E3F), onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFF5FE3D0), onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF0E4E47), onTertiaryContainer = Color(0xFFA2F4E7),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0B10), onBackground = Color(0xFFECECF3),
    surface = Color(0xFF0B0B10), onSurface = Color(0xFFECECF3),
    surfaceVariant = Color(0xFF262633), onSurfaceVariant = Color(0xFFA9A9BC),
    surfaceTint = Color(0xFFA99CFF),
    inverseSurface = Color(0xFFECECF3), inverseOnSurface = Color(0xFF1B1B22),
    outline = Color(0xFF77778E), outlineVariant = Color(0xFF2C2C3A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF30303C), surfaceDim = Color(0xFF0B0B10),
    surfaceContainerLowest = Color(0xFF060609), surfaceContainerLow = Color(0xFF111118),
    surfaceContainer = Color(0xFF16161E), surfaceContainerHigh = Color(0xFF1E1E28), surfaceContainerHighest = Color(0xFF282834),
)

internal val BrandLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B44F2), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E0FF), onPrimaryContainer = Color(0xFF1A0A6B),
    inversePrimary = Color(0xFFA99CFF),
    secondary = Color(0xFFC2185B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E3), onSecondaryContainer = Color(0xFF3E001A),
    tertiary = Color(0xFF00796B), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA6F2E6), onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7FB), onBackground = Color(0xFF14141B),
    surface = Color(0xFFF7F7FB), onSurface = Color(0xFF14141B),
    surfaceVariant = Color(0xFFE6E5F0), onSurfaceVariant = Color(0xFF4A4A5A),
    surfaceTint = Color(0xFF5B44F2),
    inverseSurface = Color(0xFF26262E), inverseOnSurface = Color(0xFFF2F1F8),
    outline = Color(0xFF7A7A8C), outlineVariant = Color(0xFFCBCAD8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFDCDBE6),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F0F7),
    surfaceContainer = Color(0xFFEBEAF3), surfaceContainerHigh = Color(0xFFE5E4EE), surfaceContainerHighest = Color(0xFFDFDEE9),
)

// ---------------------------------------------------------------- brand extras (beyond Material's roles)

/** Colours and brushes Material 3 has no role for. Read them with [BConverterTheme.brand]. */
@Immutable
data class BrandColors(
    /** The signature gradient: main actions, hero surfaces, the Export pill. White text only. */
    val signature: Brush,
    /** The gradient's stops, for places that need a solid colour of the brand (glows, progress arcs). */
    val signatureStops: List<Color>,
    /** A soft wash of the gradient for large backgrounds (hero cards, empty states). */
    val signatureWash: Brush,
    /** Completed / saved states. */
    val success: Color,
    val onSuccess: Color,
    /** The preview stage behind video: always near-black so pictures are judged on a neutral ground. */
    val stage: Color,
    /** The playhead and other "this is now" markers on the timeline. */
    val playhead: Color,
    val isDark: Boolean,
)

private val stops = listOf(Palette.Iris, Palette.Violet, Palette.Rose)

private fun brandColors(dark: Boolean) = BrandColors(
    signature = Brush.linearGradient(stops),
    signatureStops = stops,
    signatureWash = Brush.linearGradient(
        if (dark) listOf(Palette.Iris.copy(alpha = 0.28f), Palette.Rose.copy(alpha = 0.18f))
        else listOf(Palette.Iris.copy(alpha = 0.14f), Palette.Rose.copy(alpha = 0.10f)),
    ),
    success = if (dark) Palette.Success else Palette.SuccessDeep,
    onSuccess = if (dark) Color(0xFF00391F) else Color.White,
    stage = Color(0xFF000000),
    playhead = if (dark) Color(0xFFFFFFFF) else Color(0xFF14141B),
    isDark = dark,
)

internal val LocalBrandColors = staticCompositionLocalOf { brandColors(dark = true) }

object BConverterTheme {
    val brand: BrandColors
        @Composable @ReadOnlyComposable get() = LocalBrandColors.current
}

// ---------------------------------------------------------------- shape & type

internal val BConverterShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Plus Jakarta Sans (SIL OFL 1.1). Scripts it does not cover (Japanese, Chinese) fall back to the system font per glyph. */
internal val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** Tight, confident display sizes; calm body text; labels one step firmer so controls read at a glance. */
internal val BConverterTypography: Typography = Typography().let { base ->
    fun TextStyle.j(weight: FontWeight, tracking: Float? = null) =
        copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = tracking?.sp ?: letterSpacing)
    base.copy(
        displayLarge = base.displayLarge.j(FontWeight.ExtraBold, -1.2f),
        displayMedium = base.displayMedium.j(FontWeight.ExtraBold, -1f),
        displaySmall = base.displaySmall.j(FontWeight.Bold, -0.6f),
        headlineLarge = base.headlineLarge.j(FontWeight.Bold, -0.6f),
        headlineMedium = base.headlineMedium.j(FontWeight.Bold, -0.4f),
        headlineSmall = base.headlineSmall.j(FontWeight.Bold, -0.2f),
        titleLarge = base.titleLarge.j(FontWeight.Bold, -0.2f),
        titleMedium = base.titleMedium.j(FontWeight.SemiBold, 0f),
        titleSmall = base.titleSmall.j(FontWeight.SemiBold, 0.1f),
        bodyLarge = base.bodyLarge.j(FontWeight.Normal, 0.1f),
        bodyMedium = base.bodyMedium.j(FontWeight.Normal, 0.15f),
        bodySmall = base.bodySmall.j(FontWeight.Normal, 0.2f),
        labelLarge = base.labelLarge.j(FontWeight.SemiBold, 0.2f),
        labelMedium = base.labelMedium.j(FontWeight.SemiBold, 0.3f),
        labelSmall = base.labelSmall.j(FontWeight.SemiBold, 0.3f),
    )
}

// ---------------------------------------------------------------- themes

/**
 * The app theme. Brand colours by default; wallpaper (dynamic) colours on Android 12+ only when the user turns them on in
 * Settings — the brand identity is the default experience.
 */
@Composable
fun BConverterTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BrandDarkColors
        else -> BrandLightColors
    }
    CompositionLocalProvider(LocalBrandColors provides brandColors(darkTheme)) {
        MaterialTheme(colorScheme = scheme, shapes = BConverterShapes, typography = BConverterTypography, content = content)
    }
}

/**
 * The editor's "studio" look: always the dark brand palette, whatever the app theme, so colour grading and filters are
 * judged against a neutral dark surround (the convention in professional editors).
 */
@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandColors provides brandColors(dark = true)) {
        MaterialTheme(colorScheme = BrandDarkColors, shapes = BConverterShapes, typography = BConverterTypography, content = content)
    }
}
