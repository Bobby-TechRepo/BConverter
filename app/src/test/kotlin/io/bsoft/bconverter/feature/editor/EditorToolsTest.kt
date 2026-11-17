package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.project.MissingSource
import io.bsoft.bconverter.domain.timeline.EditorState
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which editor buttons are usable in which situation — decided here, not inside Composables (spec §66). */
class EditorToolsTest {
    private val timeline = TL.withAudio(
        TL.timeline(TL.video("a", 10_000), TL.video("b", 10_000), TL.video("c", 10_000)),
        listOf(TL.audio("m", 5_000, start = 2_000)),
    )

    private fun ui(
        selected: String? = null, playhead: Long = 0, t: Timeline = timeline, busy: Boolean = false,
        export: ExportUiState = ExportUiState.Idle, canUndo: Boolean = false, canRedo: Boolean = false,
        missing: List<MissingSource> = emptyList(),
    ) = EditorUiState(
        editor = EditorState(t, selected, playhead, canUndo, canRedo), busy = busy, export = export, missingSources = missing,
    )
    private fun tools(state: EditorUiState) = EditorTools.availability(state)

    @Test fun an_empty_project_can_only_add_media() {
        val t = tools(ui(t = Timeline()))
        assertTrue(t.addVideo); assertTrue(t.addAudio)
        assertFalse(t.split); assertFalse(t.delete); assertFalse(t.trim); assertFalse(t.speed); assertFalse(t.volume)
        assertFalse(t.replace); assertFalse(t.export); assertFalse(t.undo); assertFalse(t.redo)
    }

    @Test fun nothing_selected_means_no_clip_tools() {
        val t = tools(ui(playhead = 5_000))
        assertFalse(t.split); assertFalse(t.delete); assertFalse(t.trim); assertFalse(t.speed); assertFalse(t.volume); assertFalse(t.replace)
    }

    @Test fun a_selected_video_clip_offers_video_tools_but_not_volume() {
        val t = tools(ui(selected = "b", playhead = 15_000))
        assertTrue(t.split); assertTrue(t.delete); assertTrue(t.trim); assertTrue(t.speed); assertTrue(t.replace)
        assertFalse(t.volume)
    }

    @Test fun a_selected_audio_clip_offers_volume_but_not_speed() {
        val t = tools(ui(selected = "m", playhead = 3_000))
        assertTrue(t.volume); assertTrue(t.trim); assertTrue(t.delete); assertTrue(t.replace); assertTrue(t.split)
        assertTrue(t.speed)
    }

    @Test fun splitting_needs_the_playhead_strictly_inside_the_selected_clip() {
        assertFalse(tools(ui(selected = "b", playhead = 10_000)).split) // at its start
        assertTrue(tools(ui(selected = "b", playhead = 10_001)).split)
        assertTrue(tools(ui(selected = "b", playhead = 19_999)).split)
        assertFalse(tools(ui(selected = "b", playhead = 20_000)).split) // at its end
        assertFalse(tools(ui(selected = "b", playhead = 2_000)).split)  // in another clip
    }

    @Test fun video_clips_can_move_toward_the_ends_only_while_there_is_room() {
        assertFalse(tools(ui(selected = "a")).moveEarlier); assertTrue(tools(ui(selected = "a")).moveLater)
        assertTrue(tools(ui(selected = "b")).moveEarlier); assertTrue(tools(ui(selected = "b")).moveLater)
        assertTrue(tools(ui(selected = "c")).moveEarlier); assertFalse(tools(ui(selected = "c")).moveLater)
    }

    @Test fun an_audio_clip_is_not_moved_with_the_order_buttons() {
        val t = tools(ui(selected = "m"))
        assertFalse(t.moveEarlier); assertFalse(t.moveLater)
    }

    @Test fun undo_and_redo_follow_the_history() {
        assertTrue(tools(ui(canUndo = true)).undo); assertFalse(tools(ui(canUndo = true)).redo)
        assertTrue(tools(ui(canRedo = true)).redo)
    }

    @Test fun adding_and_replacing_are_off_while_a_file_is_being_prepared() {
        val t = tools(ui(selected = "a", busy = true))
        assertFalse(t.addVideo); assertFalse(t.addAudio); assertFalse(t.replace); assertFalse(t.export)
        assertTrue(t.delete)
    }

    @Test fun export_needs_video_and_no_export_in_progress() {
        assertTrue(tools(ui()).export)
        assertFalse(tools(ui(export = ExportUiState.Running(0.3f))).export)
        assertFalse(tools(ui(t = TL.withAudio(Timeline(), listOf(TL.audio("m"))))).export)
    }

    @Test fun export_is_off_while_media_is_missing() {
        assertFalse(tools(ui(missing = listOf(MissingSource(TL.source("a"), listOf("a"))))).export)
    }

    @Test fun a_selection_that_no_longer_exists_offers_no_clip_tools() {
        val t = tools(ui(selected = "gone"))
        assertFalse(t.delete); assertFalse(t.split)
    }

    // ---- layout ----
    @Test fun phones_use_the_stacked_layout_and_tablets_the_side_by_side_one() {
        assertEquals(EditorLayoutMode.PHONE, EditorLayoutMode.forSmallestWidthDp(360))
        assertEquals(EditorLayoutMode.PHONE, EditorLayoutMode.forSmallestWidthDp(599))
        assertEquals(EditorLayoutMode.TABLET, EditorLayoutMode.forSmallestWidthDp(600))
        assertEquals(EditorLayoutMode.TABLET, EditorLayoutMode.forSmallestWidthDp(840))
    }

    @Test fun the_editor_layout_follows_the_window_not_the_device() {
        assertEquals(EditorLayoutMode.PHONE, EditorLayoutMode.forWindow(io.bsoft.bconverter.core.ui.WindowLayout(411, 891)))
        // A tablet in narrow split screen gets the phone layout; an unfolded foldable on its side gets the wide one.
        assertEquals(EditorLayoutMode.PHONE, EditorLayoutMode.forWindow(io.bsoft.bconverter.core.ui.WindowLayout(500, 1280)))
        assertEquals(EditorLayoutMode.TABLET, EditorLayoutMode.forWindow(io.bsoft.bconverter.core.ui.WindowLayout(840, 673)))
        assertEquals(EditorLayoutMode.TABLET, EditorLayoutMode.forWindow(io.bsoft.bconverter.core.ui.WindowLayout(1280, 800)))
    }

    @Test fun a_transition_can_be_set_on_any_selected_video_clip_except_the_first() {
        assertFalse(tools(ui(selected = "a")).transition)
        assertTrue(tools(ui(selected = "b")).transition)
        assertTrue(tools(ui(selected = "c")).transition)
        assertFalse(tools(ui(selected = "m", playhead = 3_000)).transition)
        assertFalse(tools(ui()).transition)
    }

    @Test fun audio_can_be_detached_from_an_unmuted_video_clip() {
        assertTrue(tools(ui(selected = "b")).detach)
        assertFalse(tools(ui()).detach)
        assertFalse(tools(ui(selected = "m", playhead = 3_000)).detach)
        val muted = Timeline(listOf(TL.video("a", 10_000, muted = true))).normalized()
        assertFalse(tools(ui(selected = "a", t = muted)).detach)
        val fast = Timeline(listOf(TL.video("a", 10_000, speed = 2f))).normalized()
        assertTrue(tools(ui(selected = "a", t = fast)).detach)
    }

    @Test fun the_sound_tool_needs_a_clip_that_has_sound() {
        assertTrue(tools(ui(selected = "b", playhead = 15_000)).sound)
        assertTrue(tools(ui(selected = "m", playhead = 3_000)).sound)
        assertFalse(tools(ui()).sound)
    }
    @Test fun the_reverse_tool_needs_a_selected_video_clip() {
        assertTrue(tools(ui(selected = "b", playhead = 15_000)).reverse)
        assertFalse(tools(ui(selected = "m", playhead = 3_000)).reverse)
        assertFalse(tools(ui()).reverse)
    }
    private val pipTimeline = timeline.copy(pipClips = listOf(io.bsoft.bconverter.domain.timeline.PipClip("p", TL.source("p"), 6_000, 0, 4_000, 1_000)))

    @Test fun a_picture_in_picture_clip_needs_a_main_video_to_go_over() {
        assertTrue(tools(ui()).addPip)
        assertFalse(tools(ui(t = Timeline())).addPip)
        assertFalse(tools(ui(busy = true)).addPip)
    }
    @Test fun a_selected_picture_in_picture_clip_can_be_laid_out_trimmed_and_deleted_but_not_split() {
        val t = tools(ui(selected = "p", playhead = 2_000, t = pipTimeline))
        assertTrue(t.pipLayout); assertTrue(t.delete); assertTrue(t.trim)
        assertFalse(t.split); assertFalse(t.filter); assertFalse(t.speed); assertFalse(t.freeze)
    }
    @Test fun the_layout_tool_is_only_for_picture_in_picture_clips() {
        assertFalse(tools(ui(selected = "a", t = pipTimeline)).pipLayout)
        assertFalse(tools(ui(t = pipTimeline)).pipLayout)
    }

    @Test fun the_canvas_tool_needs_a_video_and_no_running_import() {
        assertTrue(tools(ui()).canvas)
        assertFalse(tools(ui(t = Timeline())).canvas)
        assertFalse(tools(ui(busy = true)).canvas)
    }

    @Test fun the_freeze_tool_needs_the_playhead_inside_an_ordinary_clip() {
        assertTrue(tools(ui(selected = "b", playhead = 15_000)).freeze)
        assertFalse(tools(ui(selected = "b", playhead = 10_000)).freeze) // on the cut
        assertFalse(tools(ui(selected = "m", playhead = 3_000)).freeze)
        assertFalse(tools(ui()).freeze)
        assertFalse(tools(ui(selected = "b", playhead = 15_000)).freezeLength)
    }
    @Test fun the_crop_tool_needs_a_selected_video_clip() {
        assertTrue(tools(ui(selected = "b", playhead = 15_000)).transform)
        assertFalse(tools(ui(selected = "m", playhead = 3_000)).transform)
        assertFalse(tools(ui()).transform)
    }
    @Test fun the_filter_tool_needs_a_selected_video_clip() {
        assertTrue(tools(ui(selected = "b", playhead = 15_000)).filter)
        assertFalse(tools(ui(selected = "m", playhead = 3_000)).filter)
        assertFalse(tools(ui()).filter)
    }
}
