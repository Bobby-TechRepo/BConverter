package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.overlay.AnimationKind
import io.bsoft.bconverter.domain.overlay.OverlayAnimation
import io.bsoft.bconverter.domain.overlay.OverlayBackground
import io.bsoft.bconverter.domain.overlay.OverlayEasing
import io.bsoft.bconverter.domain.overlay.OverlayGlow
import io.bsoft.bconverter.domain.overlay.OverlayOutline
import io.bsoft.bconverter.domain.overlay.OverlayShadow
import io.bsoft.bconverter.domain.overlay.OverlayTextAlign
import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.overlay.TextOverlayStyle
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerOverlayTest {
    private val full = TextOverlay(
        "t1", "Hello\nworld", 1000, 4000, centerX = 0.25f, centerY = 0.75f, rotationDeg = -12.5f, scale = 1.5f, widthFraction = 0.6f, name = "Title",
        style = TextOverlayStyle(
            fontFamily = "Roboto", fontWeight = 700, italic = true, underline = true, sizeFraction = 0.08f, color = 0xFFFFCC00.toInt(), opacity = 0.9f,
            align = OverlayTextAlign.RIGHT, letterSpacing = 0.1f, lineSpacing = 1.3f,
            outline = OverlayOutline(0xFF112233.toInt(), 0.1f), shadow = OverlayShadow(0x80000000.toInt(), 0.1f, -0.2f, 0.3f),
            glow = OverlayGlow(0xFF00FF00.toInt(), 0.4f), background = OverlayBackground(0x66FF0000, 0.5f, 0.25f),
        ),
        animIn = OverlayAnimation(AnimationKind.SLIDE_LEFT, 700, OverlayEasing.EASE_IN_OUT), animOut = OverlayAnimation(AnimationKind("future_kind"), 300, OverlayEasing.LINEAR),
    )
    private fun stored(vararg o: TextOverlay) = StoredProject(VideoProject("p", "P", Timeline(emptyList(), emptyList()), TL.exportSettings(), textOverlays = o.toList()), 1, 2)
    private fun roundTrip(s: StoredProject) = (ProjectSerializer.decode(ProjectSerializer.encode(s)) as DecodeResult.Decoded).stored

    @Test fun a_fully_styled_overlay_round_trips_unchanged() = assertEquals(listOf(full), roundTrip(stored(full)).project.textOverlays)
    @Test fun a_minimal_overlay_round_trips_unchanged() { val m = TextOverlay("m", "x", 0, 10); assertEquals(listOf(m), roundTrip(stored(m)).project.textOverlays) }
    @Test fun layer_order_is_kept() = assertEquals(listOf("b", "a"), roundTrip(stored(TextOverlay("b", "B", 0, 5), TextOverlay("a", "A", 0, 5))).project.textOverlays.map { it.id })
    @Test fun an_unknown_animation_kind_is_kept() = assertEquals("future_kind", roundTrip(stored(full)).project.textOverlays.single().animOut!!.kind.id)
    @Test fun projects_without_overlays_write_no_overlay_field() = assertFalse(ProjectSerializer.encode(stored()).contains("textOverlays"))
    @Test fun projects_saved_before_overlays_existed_open_with_none() {
        val old = ProjectSerializer.encode(stored()) // has no overlay field
        assertTrue((ProjectSerializer.decode(old) as DecodeResult.Decoded).stored.project.textOverlays.isEmpty())
    }
    @Test fun an_overlay_that_breaks_its_own_rules_makes_the_project_corrupt() {
        val bad = ProjectSerializer.encode(stored(full)).replace("\"endMs\":4000", "\"endMs\":500")
        assertTrue(ProjectSerializer.decode(bad) is DecodeResult.Corrupt)
    }
    @Test fun an_unknown_alignment_makes_the_project_corrupt() =
        assertTrue(ProjectSerializer.decode(ProjectSerializer.encode(stored(full)).replace("RIGHT", "SIDEWAYS")) is DecodeResult.Corrupt)
}
