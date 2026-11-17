package io.bsoft.bconverter.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.bsoft.bconverter.domain.overlay.OverlayAnimator
import io.bsoft.bconverter.domain.overlay.OverlayGeometry
import io.bsoft.bconverter.domain.overlay.OverlayHitTest
import io.bsoft.bconverter.domain.overlay.OverlayTextAlign
import io.bsoft.bconverter.domain.overlay.TextOverlay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws the text overlays that are shown at [playheadMs] over the picture and lets the selected one be moved, resized and
 * rotated with one or two fingers. It is drawn by the UI, not by the video player, so it does not depend on which player is
 * used. The area is the output frame: [frameAspect] is its width / height, centred and as large as fits.
 */
@Composable
fun TextOverlayLayer(
    overlays: List<TextOverlay>,
    selectedId: String?,
    playheadMs: Long,
    frameAspect: Float,
    guide: OverlayGuide,
    fonts: OverlayFonts,
    actions: TextOverlayActions,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
        val aspect = if (frameAspect.isFinite() && frameAspect > 0f) frameAspect else boxW / boxH
        val frameW = if (boxW / boxH > aspect) boxH * aspect else boxW
        val frameH = frameW / aspect
        val density = LocalDensity.current
        val currentSelected by rememberUpdatedState(selectedId)
        val currentActions by rememberUpdatedState(actions)
        val currentOverlays by rememberUpdatedState(overlays)
        val currentPlayhead by rememberUpdatedState(playheadMs)
        val currentOnEdit by rememberUpdatedState(onEdit)
        val wI = frameW.roundToInt().coerceAtLeast(1); val hI = frameH.roundToInt().coerceAtLeast(1)

        Box(
            Modifier
                .width(with(density) { frameW.toDp() }).height(with(density) { frameH.toDp() })
                // What lies outside the output frame is not in the exported video, so the preview must not show it either.
                .clipToBounds()
                .pointerInput(wI, hI) {
                    // One handler for everything, so a tap, a double tap and a drag can never get in each other's way:
                    //  - a touch that stays within the touch slop is a tap: it selects the overlay under it (or clears the selection);
                    //    a second tap on an overlay soon after opens its editor;
                    //  - when an overlay was selected at touch-down, drag / pinch / twist move it (what was moved while still inside
                    //    the slop is applied in one go when the slop is crossed, so the text never lags behind the finger);
                    //  - without a selection, touches only select and otherwise pass through untouched.
                    var lastTapAt = -1L; var lastTapPos = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // A keyframed overlay is edited with the keyframe sliders; dragging it would only change the position it starts from.
                        val canTransform = currentSelected != null
                        // A keyframed overlay's drag and pinch edit the keyframe at the playhead instead of its base position.
                        val keyed = currentOverlays.firstOrNull { it.id == currentSelected }?.takeIf { it.keyframes.isNotEmpty() }
                        fun transform(dx: Float, dy: Float, z: Float, r: Float) {
                            if (keyed != null) currentActions.onKeyframeTransform(keyed.id, currentPlayhead - keyed.startMs, dx, dy, z, r) else currentActions.onTransform(dx, dy, z, r)
                        }
                        val slop = viewConfiguration.touchSlop
                        var zoom = 1f; var rotation = 0f; var pan = Offset.Zero
                        var moved = false; var transforming = false; var cancelled = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.isConsumed && it.pressed }) { cancelled = true; break } // someone else took the touch (a release is never "taken")
                            val z = event.calculateZoom(); val r = event.calculateRotation(); val p = event.calculatePan()
                            if (!moved) {
                                zoom *= z; rotation += r; pan += p
                                if (pan.getDistance() > slop || abs(zoom - 1f) > 0.03f || abs(rotation) > 3f) {
                                    moved = true
                                    if (canTransform) {
                                        transforming = true
                                        transform(pan.x / wI, pan.y / hI, zoom, rotation)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } else if (canTransform && (z != 1f || r != 0f || p != Offset.Zero)) {
                                transform(p.x / wI, p.y / hI, z, r)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (transforming) currentActions.onGestureEnd()
                        if (cancelled || moved) return@awaitEachGesture
                        val hit = OverlayHitTest.topmostAt(currentOverlays, currentPlayhead, down.position.x, down.position.y, wI, hI)
                        val again = lastTapAt >= 0 && down.uptimeMillis - lastTapAt <= viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastTapPos).getDistance() <= slop * 4
                        if (again) {
                            lastTapAt = -1L
                            if (hit != null) { currentActions.onSelect(hit); currentOnEdit() }
                        } else {
                            lastTapAt = down.uptimeMillis; lastTapPos = down.position
                            currentActions.onSelect(hit)
                        }
                    }
                },
        ) {
            overlays.forEach { o ->
                val frame = OverlayAnimator.frameAt(o, playheadMs) ?: return@forEach
                if (o.isImage) OverlayPicture(o, o.id == selectedId, frame, wI, hI) else OverlayText(o, o.id == selectedId, frame, wI, hI, fonts)
            }
            if (guide.vertical || guide.horizontal) {
                val color = MaterialTheme.colorScheme.primary
                Box(Modifier.fillMaxSize().drawBehind {
                    if (guide.vertical) drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
                    if (guide.horizontal) drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f)
                })
            }
        }
    }
}

@Composable
private fun OverlayText(o: TextOverlay, selected: Boolean, frame: io.bsoft.bconverter.domain.overlay.OverlayFrame, frameW: Int, frameH: Int, fonts: OverlayFonts) {
    val density = LocalDensity.current
    val px = OverlayGeometry.toPixels(o, frameW, frameH)
    val s = o.style
    val fontPx = px.fontSizePx
    val size = with(density) { fontPx.toSp() }
    val face = fonts.typeface(s)
    val base = TextStyle(
        fontSize = size, fontFamily = face, fontWeight = FontWeight(s.fontWeight), fontStyle = if (s.italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = s.letterSpacing.em, lineHeight = size * 1.2f * s.lineSpacing,
        textAlign = when (s.align) { OverlayTextAlign.LEFT -> TextAlign.Left; OverlayTextAlign.CENTER -> TextAlign.Center; OverlayTextAlign.RIGHT -> TextAlign.Right },
        textDecoration = if (s.underline) TextDecoration.Underline else null,
    )
    val maxWidthPx = px.boxWidthPx
    val cx = (o.centerX + frame.offsetX) * frameW
    val cy = (o.centerY + frame.offsetY) * frameH
    val selectedColor = MaterialTheme.colorScheme.primary
    val box = Modifier
        .layout { measurable, constraints ->
            val limit = maxWidthPx?.roundToInt()?.coerceAtLeast(1) ?: constraints.maxWidth
            val p = measurable.measure(Constraints(maxWidth = limit.coerceAtMost(constraints.maxWidth.coerceAtLeast(1))))
            layout(constraints.maxWidth, constraints.maxHeight) { p.placeRelative(IntOffset((cx - p.width / 2f).roundToInt(), (cy - p.height / 2f).roundToInt())) }
        }
        .semantics { contentDescription = o.text }
    // The outer box places the text at its spot; the inner one rotates and scales it about its own centre.
    Box(box) { Box(Modifier.graphicsLayer { rotationZ = o.rotationDeg + frame.rotationDeg; scaleX = frame.scale; scaleY = frame.scale; alpha = s.opacity * frame.alpha }) {
        val pad = with(density) { ((s.background?.padding ?: 0f) * fontPx).toDp() }
        val bg = s.background?.let { Modifier.background(Color(it.color), RoundedCornerShape(with(density) { (it.cornerRadius * fontPx).toDp() })) } ?: Modifier
        val sel = if (selected) Modifier.border(1.5.dp, selectedColor) else Modifier
        Box(sel.then(bg).padding(pad)) {
            s.glow?.let { g ->
                Text(o.text, style = base.copy(color = Color(g.color), shadow = Shadow(Color(g.color), Offset.Zero, g.radius * fontPx)))
            }
            s.outline?.let { ol ->
                Text(o.text, style = base.copy(color = Color(ol.color), drawStyle = Stroke(width = ol.widthFraction * fontPx * 2f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
            }
            Text(
                o.text,
                style = base.copy(color = Color(s.color), shadow = s.shadow?.let { Shadow(Color(it.color), Offset(it.dx * fontPx, it.dy * fontPx), it.blur * fontPx) }),
            )
        }
    } }
}

/** A picture overlay: the stored file drawn at its size on the frame, turned and faded like the exported one. */
@Composable
private fun OverlayPicture(o: TextOverlay, selected: Boolean, frame: io.bsoft.bconverter.domain.overlay.OverlayFrame, frameW: Int, frameH: Int) {
    val image = o.image ?: return
    val density = LocalDensity.current
    val (pw, ph) = OverlayGeometry.imageSizePx(o, frameW, frameH)
    // Decoded at about the size it is shown, so a big picture does not use more memory than it needs.
    val bitmap = androidx.compose.runtime.remember(image.path, pw.roundToInt(), ph.roundToInt()) { PictureCache.load(image.path, pw.roundToInt(), ph.roundToInt()) }
    val cx = (o.centerX + frame.offsetX) * frameW
    val cy = (o.centerY + frame.offsetY) * frameH
    val selectedColor = MaterialTheme.colorScheme.primary
    val description = o.name ?: androidx.compose.ui.res.stringResource(io.bsoft.bconverter.R.string.image_overlay_name)
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(Constraints.fixed(pw.roundToInt().coerceAtLeast(1), ph.roundToInt().coerceAtLeast(1)))
                layout(constraints.maxWidth, constraints.maxHeight) { p.placeRelative(IntOffset((cx - p.width / 2f).roundToInt(), (cy - p.height / 2f).roundToInt())) }
            }
            .graphicsLayer { rotationZ = o.rotationDeg + frame.rotationDeg; scaleX = frame.scale; scaleY = frame.scale; alpha = o.style.opacity * frame.alpha }
            .then(if (selected) Modifier.border(1.5.dp, selectedColor) else Modifier)
            .semantics { contentDescription = description },
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
        } else {
            // The file is gone or unreadable: show where it would be, so it can still be selected and deleted.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)))
        }
    }
}
