package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class TextOverlayLayersTest {
    private val l = listOf("a", "b", "c", "d").map { TextOverlay(it, it, 0, 10) }
    private fun order(e: OverlayEdit) = (e as OverlayEdit.Ok).overlays.map { it.id }

    @Test fun bring_to_front() = assertEquals(listOf("b", "c", "d", "a"), order(TextOverlayEditor.bringToFront(l, "a")))
    @Test fun send_to_back() = assertEquals(listOf("d", "a", "b", "c"), order(TextOverlayEditor.sendToBack(l, "d")))
    @Test fun forward_one_step() = assertEquals(listOf("a", "c", "b", "d"), order(TextOverlayEditor.bringForward(l, "b")))
    @Test fun backward_one_step() = assertEquals(listOf("a", "c", "b", "d"), order(TextOverlayEditor.sendBackward(l, "c")))
    @Test fun the_top_cannot_go_further_forward() = assertEquals(listOf("a", "b", "c", "d"), order(TextOverlayEditor.bringForward(l, "d")))
    @Test fun the_bottom_cannot_go_further_back() = assertEquals(listOf("a", "b", "c", "d"), order(TextOverlayEditor.sendBackward(l, "a")))
    @Test fun an_unknown_id_is_rejected() = assertEquals(OverlayProblem.NoSuchOverlay, (TextOverlayEditor.bringToFront(l, "x") as OverlayEdit.Rejected).reason)
    @Test fun layer_changes_keep_every_overlay() = assertEquals(l.map { it.id }.toSet(), order(TextOverlayEditor.sendToBack(l, "c")).toSet())
}
