package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.overlay.TextOverlay
import io.bsoft.bconverter.domain.subtitle.SubtitleCue
import io.bsoft.bconverter.domain.subtitle.SubtitleTrack
import io.bsoft.bconverter.domain.timeline.EditorState
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which inline tool panel shows (step 23, part 2). */
class EditorPanelsTest {
    private val timeline = TL.withAudio(
        TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000)),
        listOf(TL.audio("m", 5_000, start = 2_000)),
    )
    private val track = SubtitleTrack("s", "Subtitles 1", listOf(SubtitleCue("c1", 0, 2_000, "Hi")))

    private fun ui(
        selected: String? = null, t: Timeline = timeline, overlay: String? = null, cue: CueRef? = null, picking: Set<String>? = null,
    ) = EditorUiState(
        editor = EditorState(t, selected, 0, false, false),
        textOverlays = listOf(TextOverlay("o", "Hello", 0, 3_000)),
        selectedOverlayId = overlay,
        subtitleTracks = listOf(track),
        selectedCue = cue,
        multiSelection = picking,
    )

    private fun shown(panel: EditorPanel, state: EditorUiState) = EditorPanels.shown(panel, state, EditorTools.availability(state))

    @Test fun a_clip_panel_shows_while_its_tool_applies_to_the_selection() {
        assertEquals(EditorPanel.FILTER, shown(EditorPanel.FILTER, ui(selected = "a")))
        assertEquals(EditorPanel.SPEED, shown(EditorPanel.SPEED, ui(selected = "b")))
        assertEquals(EditorPanel.VOLUME, shown(EditorPanel.VOLUME, ui(selected = "m")))
    }

    @Test fun picking_another_kind_of_clip_closes_a_panel_that_does_not_apply_to_it() {
        // Filters are for video; an audio clip has none.
        assertEquals(EditorPanel.NONE, shown(EditorPanel.FILTER, ui(selected = "m")))
        // The first clip has no transition before it.
        assertEquals(EditorPanel.NONE, shown(EditorPanel.TRANSITION, ui(selected = "a")))
        assertEquals(EditorPanel.TRANSITION, shown(EditorPanel.TRANSITION, ui(selected = "b")))
    }

    @Test fun deselecting_closes_a_clip_panel_but_not_the_canvas_or_subtitles() {
        assertEquals(EditorPanel.NONE, shown(EditorPanel.TRIM, ui()))
        assertEquals(EditorPanel.CANVAS, shown(EditorPanel.CANVAS, ui()))
        assertEquals(EditorPanel.CANVAS, shown(EditorPanel.CANVAS, ui(selected = "a")))
        assertEquals(EditorPanel.SUBTITLES, shown(EditorPanel.SUBTITLES, ui()))
    }

    @Test fun the_canvas_needs_a_video() {
        assertEquals(EditorPanel.NONE, shown(EditorPanel.CANVAS, ui(t = Timeline())))
    }

    @Test fun the_text_panel_needs_a_selected_overlay_that_still_exists() {
        assertEquals(EditorPanel.TEXT, shown(EditorPanel.TEXT, ui(overlay = "o")))
        assertEquals(EditorPanel.NONE, shown(EditorPanel.TEXT, ui()))
        assertEquals(EditorPanel.NONE, shown(EditorPanel.TEXT, ui(overlay = "gone")))
    }

    @Test fun a_picked_cue_is_edited_on_top_of_the_open_panel() {
        assertEquals(EditorPanel.CUE, shown(EditorPanel.SUBTITLES, ui(cue = CueRef("s", "c1"))))
        assertEquals(EditorPanel.CUE, shown(EditorPanel.NONE, ui(cue = CueRef("s", "c1"))))
        // A cue that no longer exists is not edited.
        assertEquals(EditorPanel.SUBTITLES, shown(EditorPanel.SUBTITLES, ui(cue = CueRef("s", "gone"))))
    }

    @Test fun picking_several_clips_shows_no_panel() {
        assertEquals(EditorPanel.NONE, shown(EditorPanel.FILTER, ui(selected = "a", picking = setOf("a"))))
        assertEquals(EditorPanel.NONE, shown(EditorPanel.CANVAS, ui(picking = emptySet())))
    }

    @Test fun nothing_requested_shows_nothing() {
        assertEquals(EditorPanel.NONE, shown(EditorPanel.NONE, ui(selected = "a")))
        // CUE is only ever shown for a picked cue, never because it was asked for.
        assertEquals(EditorPanel.NONE, shown(EditorPanel.CUE, ui()))
    }

    @Test fun tapping_the_open_tool_closes_it_and_another_tool_replaces_it() {
        assertEquals(EditorPanel.NONE, EditorPanels.toggled(EditorPanel.SPEED, EditorPanel.SPEED))
        assertEquals(EditorPanel.FILTER, EditorPanels.toggled(EditorPanel.SPEED, EditorPanel.FILTER))
        assertEquals(EditorPanel.TRIM, EditorPanels.toggled(EditorPanel.NONE, EditorPanel.TRIM))
    }
}
