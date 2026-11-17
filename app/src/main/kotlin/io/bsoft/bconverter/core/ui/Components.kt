package io.bsoft.bconverter.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.SavedOutput

// ================================================================ surfaces

/**
 * A soft brand glow behind the top of a screen. Put it first in a full-screen Box; it draws nothing interactive.
 * Kept very low-contrast so text on top always reads against the plain surface colour.
 */
@Composable
fun BrandBackdrop(modifier: Modifier = Modifier) {
    val brand = BConverterTheme.brand
    val alpha = if (brand.isDark) 0.22f else 0.12f
    Canvas(modifier.fillMaxSize()) {
        val r = size.maxDimension * 0.75f
        drawCircle(
            Brush.radialGradient(listOf(brand.signatureStops[0].copy(alpha = alpha), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = r),
            radius = r, center = Offset(size.width * 0.1f, 0f),
        )
        drawCircle(
            Brush.radialGradient(listOf(brand.signatureStops[2].copy(alpha = alpha * 0.7f), Color.Transparent), center = Offset(size.width, size.height * 0.05f), radius = r * 0.8f),
            radius = r * 0.8f, center = Offset(size.width, size.height * 0.05f),
        )
    }
}

/** A tonal card with a hairline edge: the default container for grouped content. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val edge = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.then(edge), shape = shape, color = color) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Surface(modifier = modifier.then(edge), shape = shape, color = color) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/** An error or problem message on an error-container surface; announced to screen readers when it appears. */
@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                // Decorative: the message itself is what TalkBack reads.
                Modifier.size(22.dp).background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f), CircleShape).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) { Text("!", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Heading that groups related controls; marked as a heading for TalkBack. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).clip(CircleShape).background(BConverterTheme.brand.signature))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A rounded, gradient-filled badge holding an icon, with a soft glow; anchors empty, busy and finished states. */
@Composable
fun IconBadge(@DrawableRes icon: Int, size: Dp = 72.dp, modifier: Modifier = Modifier, brush: Brush? = null) {
    val brand = BConverterTheme.brand
    Box(modifier.size(size * 1.5f), contentAlignment = Alignment.Center) {
        // Glow: a radial wash larger than the badge.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(brand.signatureStops[1].copy(alpha = if (brand.isDark) 0.35f else 0.22f), Color.Transparent)))
        }
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(brush ?: brand.signature),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(size * 0.46f), tint = Color.White)
        }
    }
}

/** A small rounded square holding an icon on a tonal background; used in lists and tool tiles. */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, container: Color = MaterialTheme.colorScheme.surfaceContainerHighest, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(container), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(size * 0.5f), tint = tint)
    }
}

// ================================================================ buttons

/** The full-width main action: a 56 dp gradient pill. Disabled, it falls back to a quiet tonal fill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    GradientButton(text, onClick, modifier.fillMaxWidth(), enabled, minHeight = 56.dp)
}

/** A gradient pill button sized to its label; the brand's single loudest control. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    @DrawableRes leadingIcon: Int? = null,
) {
    val brand = BConverterTheme.brand
    // The gradient is painted behind the button (clipped to its pill) and the button's own container is transparent,
    // so the fill always matches the button's size whether it wraps its label or fills the width.
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(brand.signature) else Modifier),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        elevation = null,
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A quiet secondary action: hairline outline, pill shape, 48 dp. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        modifier = modifier.heightIn(min = 48.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

// ================================================================ progress

/**
 * A ring that fills with the signature gradient; [fraction] in 0..1. Exposes its value to accessibility services.
 * The centre is free for an icon or a number.
 */
@Composable
fun GradientProgressRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 168.dp, stroke: Dp = 10.dp, center: @Composable BoxScope.() -> Unit = {}) {
    val brand = BConverterTheme.brand
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450), label = "ring")
    Box(
        modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            // A sweep gradient starts at 3 o'clock; turning the canvas makes the arc and its colours start at 12.
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(brand.signatureStops + brand.signatureStops.first()),
                    0f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

/** A thin gradient progress bar. */
@Composable
fun GradientLinearProgress(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(300), label = "bar")
    val brand = BConverterTheme.brand
    Box(
        modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(animated).height(6.dp).clip(CircleShape).background(brand.signature))
    }
}

// ================================================================ stage panels

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BrandBackdrop()
        Column(
            Modifier.fillMaxSize().padding(24.dp).readableWidth(480.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** First screen of a tool: a drop-zone card that says what to do, one clear action, and any problem from the last attempt. */
@Composable
fun SelectInputPanel(
    @DrawableRes icon: Int, hint: String, buttonText: String, error: String?, onSelect: () -> Unit,
    /** A second, quieter way in under the main button (the converter's "several videos"). */
    secondaryText: String? = null, onSecondary: () -> Unit = {},
) {
    val dash = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    CenteredColumn {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                .drawBehind {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        dash, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                }
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(icon, size = 80.dp)
            Spacer(Modifier.height(16.dp))
            Text(hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            ErrorBanner(error)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(buttonText, onSelect)
        if (secondaryText != null) {
            Spacer(Modifier.height(12.dp))
            QuietButton(secondaryText, onSecondary, Modifier.fillMaxWidth())
        }
    }
}

/** Progress while converting or extracting: a gradient ring around the tool's icon, the status line, and a clear way out. */
@Composable
fun ProgressPanel(@DrawableRes icon: Int, fraction: Float, label: String, cancelText: String, onCancel: () -> Unit) {
    CenteredColumn {
        GradientProgressRing(fraction) {
            IconTile(icon, size = 64.dp, container = MaterialTheme.colorScheme.surfaceContainerHigh, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(28.dp))
        QuietButton(cancelText, onCancel, Modifier.widthIn(min = 160.dp))
    }
}

/** Success state: confirmation, Share as the main action, then "do another". */
@Composable
fun CompletedPanel(message: String, saved: SavedOutput?, onShare: (SavedOutput) -> Unit, anotherText: String, onAnother: () -> Unit) {
    val brand = BConverterTheme.brand
    CenteredColumn {
        IconBadge(R.drawable.ic_check, size = 88.dp, brush = Brush.linearGradient(listOf(brand.success, brand.success.copy(alpha = 0.75f))))
        Spacer(Modifier.height(20.dp))
        Text(
            message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(28.dp))
        if (saved != null) {
            PrimaryButton(stringResource(R.string.share), { onShare(saved) })
            Spacer(Modifier.height(10.dp))
        }
        QuietButton(anotherText, onAnother, Modifier.fillMaxWidth())
    }
}
