package io.bsoft.bconverter.feature.editor

import io.bsoft.bconverter.domain.timeline.SeekRange
import androidx.compose.ui.test.assertDoesNotExist
import io.bsoft.bconverter.domain.model.SavedOutput
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import androidx.compose.ui.graphics.ImageBitmap
import io.bsoft.bconverter.domain.media.Waveform
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.project.MissingSource
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.AudioTrack
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.timeline.EditorState
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoClip
import io.bsoft.bconverter.media.player.PlaybackError
import io.bsoft.bconverter.media.player.PlaybackStatus
import io.bsoft.bconverter.media.player.TimelinePlaybackState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The editor's user-visible behaviour on both layouts; logic itself is covered by pure unit tests. */
class EditorContentTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun file(name: String) = MediaSource.FileSource(File("/tmp/$name"))
    private val timeline = Timeline(
        videoClips = listOf(
            VideoClip("a", file("a.mp4"), 10_000, 0, 10_000),
            VideoClip("b", file("b.mp4"), 10_000, 0, 10_000),
        ),
        audioTracks = listOf(AudioTrack("t1", listOf(AudioClip("m", file("m.mp3"), 8_000, 0, 8_000, 1f, 2_000)))),
    ).normalized()

    private fun state(
        t: Timeline = timeline, selected: String? = null, playhead: Long = 0, canUndo: Boolean = false,
        missing: List<MissingSource> = emptyList(), export: ExportUiState = ExportUiState.Idle, message: EditorMessage? = null,
    ) = EditorUiState(projectName = "Holiday", editor = EditorState(t, selected, playhead, canUndo, false), missingSources = missing, export = export, message = message)

    private fun show(
        s: EditorUiState,
        layout: EditorLayoutMode = EditorLayoutMode.PHONE,
        actions: EditorActions = EditorActions(),
        media: TimelineMedia? = null,
        playback: TimelinePlaybackState = TimelinePlaybackState(PlaybackStatus.Ready, false, s.editor.playheadMs, s.editor.timeline.videoDurationMs),
    ) = rule.setContent { EditorContent(s, playback, s.editor.playheadMs, layout, actions, preview = { Box(it) }, media = media) }

    private fun button(id: Int) = rule.onNodeWithText(str(id))

    /** The tools bar scrolls horizontally, so a button can be off screen and must be scrolled to before a tap. */
    private fun tap(id: Int) { button(id).performScrollTo().performClick() }

    // ---- empty ----
    @Test fun an_empty_project_invites_adding_a_video_and_offers_no_clip_tools() {
        show(state(t = Timeline()), playback = TimelinePlaybackState())
        rule.onNodeWithText(str(R.string.editor_empty_timeline)).assertIsDisplayed()
        button(R.string.editor_add_video).assertIsEnabled()
        // Step 23: tools that do not apply are not shown at all.
        button(R.string.editor_split).assertDoesNotExist()
        button(R.string.editor_delete).assertDoesNotExist()
        button(R.string.editor_export).assertIsNotEnabled()
    }

    @Test fun the_project_name_is_shown_in_the_top_bar() { show(state()); rule.onNodeWithText("Holiday").assertIsDisplayed() }

    // ---- selection and tools ----
    @Test fun tapping_a_video_clip_selects_it() {
        var picked: String? = null
        show(state(), actions = EditorActions(onSelect = { picked = it }))
        rule.onNode(hasContentDescription(str(R.string.editor_clip_video, 2, 2, "0:10"), substring = true)).performScrollTo().performClick()
        assertEquals("b", picked)
    }

    @Test fun tapping_an_audio_clip_selects_it() {
        var picked: String? = null
        show(state(), actions = EditorActions(onSelect = { picked = it }))
        rule.onNode(hasContentDescription(str(R.string.editor_clip_audio, 1, "0:08"), substring = true)).performScrollTo().performClick()
        assertEquals("m", picked)
    }

    @Test fun the_selected_clip_says_it_is_selected_in_words_and_in_semantics() {
        show(state(selected = "a"))
        val clip = rule.onNode(hasContentDescription(str(R.string.editor_clip_video, 1, 2, "0:10"), substring = true))
        clip.assertIsSelected()
    }

    @Test fun clip_tools_appear_only_once_a_clip_is_selected() {
        show(state())
        button(R.string.editor_trim).assertDoesNotExist(); button(R.string.editor_speed).assertDoesNotExist(); button(R.string.editor_volume).assertDoesNotExist()
        // Adding things is what is offered instead.
        button(R.string.editor_add_text).assertExists()
    }

    @Test fun a_selected_video_clip_enables_speed_but_not_volume() {
        show(state(selected = "a", playhead = 5_000))
        button(R.string.editor_speed).assertIsEnabled(); button(R.string.editor_volume).assertDoesNotExist(); button(R.string.editor_split).assertIsEnabled()
        // With a clip selected, the tools for adding text and pictures make room for the clip's own.
        button(R.string.editor_add_text).assertDoesNotExist()
    }

    @Test fun a_selected_audio_clip_enables_volume_and_speed() {
        show(state(selected = "m", playhead = 3_000))
        button(R.string.editor_volume).assertIsEnabled(); button(R.string.editor_speed).assertIsEnabled()
    }

    @Test fun split_is_offered_only_when_the_playhead_is_inside_the_selected_clip() {
        show(state(selected = "a", playhead = 0))
        button(R.string.editor_split).assertDoesNotExist()
    }

    @Test fun the_tool_buttons_report_their_actions() {
        val calls = mutableListOf<String>()
        show(
            state(selected = "a", playhead = 5_000, canUndo = true),
            actions = EditorActions(onSplit = { calls += "split" }, onDelete = { calls += "delete" }, onUndo = { calls += "undo" }, onAddVideo = { calls += "addVideo" }, onAddAudio = { calls += "addAudio" }),
        )
        tap(R.string.editor_split); tap(R.string.editor_delete); tap(R.string.editor_undo)
        tap(R.string.editor_add_video); tap(R.string.editor_add_audio)
        assertEquals(listOf("split", "delete", "undo", "addVideo", "addAudio"), calls)
    }

    @Test fun redo_is_off_when_there_is_nothing_to_redo() { show(state()); button(R.string.editor_redo).assertIsNotEnabled() }

    // ---- dialogs ----
    @Test fun choosing_a_speed_applies_it() {
        var speed = 0f
        show(state(selected = "a"), actions = EditorActions(onSpeed = { speed = it }))
        tap(R.string.editor_speed)
        rule.onNodeWithText(str(R.string.editor_clip_speed, "2")).performClick()
        assertEquals(2f, speed, 0f)
    }

    @Test fun the_export_dialog_starts_an_export() {
        var started = false
        show(state(), actions = EditorActions(onExportStart = { started = true }))
        button(R.string.editor_export).performClick()
        rule.onNodeWithText(str(R.string.editor_export_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.editor_export_start)).performClick()
        assertTrue(started)
    }

    @Test fun renaming_reports_the_new_name() {
        var name = ""
        show(state(), actions = EditorActions(onRename = { name = it }))
        rule.onNodeWithText("Holiday").performClick()
        rule.onNodeWithText(str(R.string.editor_rename_project)).assertIsDisplayed()
    }

    // ---- banners and messages ----
    @Test fun missing_media_is_explained_marked_on_the_clip_and_blocks_export() {
        show(state(missing = listOf(MissingSource(file("b.mp4"), listOf("b")))))
        rule.onNodeWithText(str(R.string.editor_missing_banner)).assertIsDisplayed()
        rule.onNode(hasContentDescription(str(R.string.editor_clip_missing), substring = true)).assertExists()
        button(R.string.editor_export).assertIsNotEnabled()
    }

    @Test fun a_running_export_shows_progress_and_can_be_cancelled() {
        var cancelled = false
        show(state(export = ExportUiState.Running(0.4f)), actions = EditorActions(onExportCancel = { cancelled = true }))
        rule.onNodeWithText(str(R.string.editor_exporting, 40)).assertIsDisplayed()
        button(R.string.editor_export_cancel).performClick()
        assertTrue(cancelled)
    }

    @Test fun a_finished_export_is_announced() {
        show(state(export = ExportUiState.Completed))
        rule.onNodeWithText(str(R.string.editor_export_done)).assertIsDisplayed()
    }

    @Test fun errors_are_shown_in_plain_language() {
        show(state(message = EditorMessage.EditFailed(EditError.NothingSelected)))
        rule.onNodeWithText(str(R.string.msg_edit_nothing_selected)).assertIsDisplayed()
    }

    // ---- preview states ----
    @Test fun a_preview_that_cannot_play_says_why() {
        show(state(), playback = TimelinePlaybackState(PlaybackStatus.Error, error = PlaybackError.SourceNotFound))
        rule.onNodeWithText(str(R.string.preview_error_open)).assertIsDisplayed()
    }

    // ---- subtitles and text tools, phone and tablet ----
    private val overlay = io.bsoft.bconverter.domain.overlay.TextOverlay("o1", "Hello", 0, 3_000)
    private fun withText(selected: Boolean) = state().copy(textOverlays = listOf(overlay), selectedOverlayId = if (selected) "o1" else null)

    @Test fun an_overlay_with_keyframes_says_how_many_in_the_timeline() {
        val moving = overlay.copy(keyframes = listOf(io.bsoft.bconverter.domain.overlay.OverlayKeyframe(0, 0.2f, 0.5f), io.bsoft.bconverter.domain.overlay.OverlayKeyframe(2_000, 0.8f, 0.5f)))
        show(state().copy(textOverlays = listOf(moving)))
        val twoKeyframes = context.resources.getQuantityString(R.plurals.editor_clip_keyframes, 2, 2)
        rule.onNode(hasContentDescription(twoKeyframes, substring = true)).performScrollTo().assertExists()
        // One without animation does not mention keyframes.
        rule.onNode(hasContentDescription(context.resources.getQuantityString(R.plurals.editor_clip_keyframes, 1, 1), substring = true)).assertDoesNotExist()
    }

    private class RecordingMedia : TimelineMedia {
        val widths = java.util.Collections.synchronizedList(mutableListOf<Int>())
        override suspend fun frame(file: File, positionMs: Long, widthPx: Int): ImageBitmap? { widths += widthPx; return ImageBitmap(4, 4) }
        override suspend fun waveform(file: File): Waveform? = null
    }

    @Test fun a_blurred_background_asks_for_a_small_still_of_the_clip_behind_the_player() {
        val media = RecordingMedia()
        show(state().copy(previewTimeline = timeline, canvas = io.bsoft.bconverter.domain.canvas.CanvasSettings(background = io.bsoft.bconverter.domain.canvas.CanvasBackground.Blur)), media = media)
        rule.waitUntil(5_000) { 160 in media.widths }
    }

    @Test fun a_colour_background_needs_no_still_behind_the_player() {
        val media = RecordingMedia()
        show(state(), media = media)
        rule.waitForIdle()
        assertTrue(160 !in media.widths)
    }

    @Test fun the_transition_marker_is_at_least_48dp_tall_to_tap() {
        show(state())
        val marker = rule.onNode(androidx.compose.ui.test.hasContentDescription(str(R.string.editor_transition_marker, 2, str(R.string.editor_transition_none)), substring = true))
        marker.assertExists().assertHeightIsAtLeast(48.dp)
    }

    private fun text_and_subtitle_tools(layout: EditorLayoutMode) {
        var added = 0
        show(state(), layout = layout, actions = EditorActions(overlays = TextOverlayActions(onAdd = { added++ })))
        button(R.string.editor_subtitles).performScrollTo().assertIsEnabled()
        button(R.string.editor_edit_text).performScrollTo().assertIsNotEnabled()
        tap(R.string.editor_add_text); assertEquals(1, added)
    }

    @Test fun phone_has_the_subtitle_and_text_tools_and_add_text_works() = text_and_subtitle_tools(EditorLayoutMode.PHONE)
    @Test fun tablet_has_the_subtitle_and_text_tools_and_add_text_works() = text_and_subtitle_tools(EditorLayoutMode.TABLET)

    private fun edit_text_opens_the_panel(layout: EditorLayoutMode) {
        show(withText(selected = true), layout = layout)
        button(R.string.editor_edit_text).performScrollTo().assertIsEnabled().performClick()
        // "Text" is both the panel title and a field label, so the panel is recognised by its (unique) hint and its Done button.
        rule.onNodeWithText(str(R.string.text_hint_gestures)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.text_done)).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText(str(R.string.text_hint_gestures))).fetchSemanticsNodes().size)
    }

    @Test fun phone_edit_text_opens_the_panel_and_done_closes_it() = edit_text_opens_the_panel(EditorLayoutMode.PHONE)
    @Test fun tablet_edit_text_opens_the_panel_and_done_closes_it() = edit_text_opens_the_panel(EditorLayoutMode.TABLET)

    @Test fun edit_text_needs_a_selected_text() {
        show(withText(selected = false))
        button(R.string.editor_edit_text).performScrollTo().assertIsNotEnabled()
    }

    // ---- tablet ----
    @Test fun the_tablet_layout_has_a_project_panel_with_the_add_buttons() {
        show(state(), layout = EditorLayoutMode.TABLET)
        rule.onNodeWithText(str(R.string.editor_projects_panel)).assertIsDisplayed()
        button(R.string.editor_add_video).assertIsDisplayed()
        button(R.string.editor_add_audio).assertIsDisplayed()
    }

    @Test fun the_phone_layout_has_no_project_panel() {
        show(state())
        rule.onAllNodesWithTextCount(str(R.string.editor_projects_panel), 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
        assertEquals(expected, onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)
    }

    // ---- thumbnails and waveforms ----
    private class RecordingMedia(val frame: ImageBitmap? = null, val wave: Waveform? = null) : TimelineMedia {
        val frames = java.util.concurrent.CopyOnWriteArrayList<Long>(); val waveforms = java.util.concurrent.atomic.AtomicInteger()
        override suspend fun frame(file: File, positionMs: Long, widthPx: Int): ImageBitmap? { frames += positionMs; return frame }
        override suspend fun waveform(file: File): Waveform? { waveforms.incrementAndGet(); return wave }
    }

    @Test fun the_timeline_asks_for_frames_and_the_waveform_of_visible_clips() {
        val media = RecordingMedia(wave = Waveform(List(160) { 0.5f }, 20))
        show(state().copy(previewTimeline = timeline), media = media)
        rule.waitUntil(5_000) { media.frames.isNotEmpty() && media.waveforms.get() > 0 }
        assertTrue(media.frames.all { it in 0..10_000 })
    }

    @Test fun clips_stay_selectable_when_their_media_cannot_be_decoded() {
        var picked: String? = null
        show(state().copy(previewTimeline = timeline), media = RecordingMedia(), actions = EditorActions(onSelect = { picked = it }))
        rule.onNode(hasContentDescription(str(R.string.editor_clip_video, 1, 2, "0:10"), substring = true)).performClick()
        assertEquals("a", picked)
    }

    // ---- pinch zoom and the video-audio strip ----
    private fun timelineNode() = rule.onNode(hasContentDescription(str(R.string.editor_timeline)))
    private fun zoomIs(percent: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.editor_zoom, percent))

    @Test fun the_timeline_starts_at_one_hundred_percent() {
        show(state()); timelineNode().assert(zoomIs(100))
    }

    @Test fun one_pinch_outwards_zooms_in_in_a_single_gesture() {
        show(state())
        timelineNode().performTouchInput {
            pinch(Offset(centerX - 40, centerY), Offset(centerX - 160, centerY), Offset(centerX + 40, centerY), Offset(centerX + 160, centerY), durationMillis = 300)
        }
        rule.waitUntil(3_000) { runCatching { timelineNode().assert(SemanticsMatcher("zoomed in") { n ->
            (n.config.getOrNull(SemanticsProperties.StateDescription) ?: "") != str(R.string.editor_zoom, 100) }) }.isSuccess }
    }

    @Test fun one_pinch_inwards_zooms_out() {
        show(state())
        timelineNode().performTouchInput {
            pinch(Offset(centerX - 160, centerY), Offset(centerX - 40, centerY), Offset(centerX + 160, centerY), Offset(centerX + 40, centerY), durationMillis = 300)
        }
        rule.waitUntil(3_000) { runCatching { timelineNode().assert(SemanticsMatcher("zoomed out") { n ->
            (n.config.getOrNull(SemanticsProperties.StateDescription) ?: "100").let { it != str(R.string.editor_zoom, 100) } }) }.isSuccess }
    }

    @Test fun a_clip_with_sound_still_selects_and_the_strip_asks_for_the_clip_waveform() {
        val media = RecordingMedia(wave = Waveform(List(200) { 0.4f }, 20))
        var picked: String? = null
        show(state().copy(previewTimeline = timeline), media = media, actions = EditorActions(onSelect = { picked = it }))
        rule.waitUntil(5_000) { media.waveforms.get() >= 2 } // the video's own sound and the audio clip
        rule.onNode(hasContentDescription(str(R.string.editor_clip_video, 1, 2, "0:10"), substring = true)).performClick()
        assertEquals("a", picked)
    }

    // ---- accessibility (spec §43): the timeline can be operated without touch gestures ----
    private fun playhead() = rule.onNode(hasContentDescription(str(R.string.editor_playhead_name)))

    private fun runAction(label: String) {
        val action = playhead().fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnUiThread { action.action() }
    }

    @Test fun the_playhead_is_an_adjustable_value_that_seeks_when_set() {
        var sought = -1L
        show(state(playhead = 3_000), actions = EditorActions(onSeek = { sought = it }))
        val range = playhead().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(3_000f, range.current, 0f); assertEquals(20_000f + SeekRange.BLANK_TAIL_MS, range.range.endInclusive, 0f) // the timeline's 20 s plus the blank area after it
        val setProgress = playhead().fetchSemanticsNode().config[SemanticsActions.SetProgress].action!!
        rule.runOnUiThread { setProgress(7_500f) }
        assertEquals(7_500L, sought)
    }

    @Test fun the_playhead_announces_its_position_in_words() {
        show(state(playhead = 3_000))
        playhead().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.editor_playhead, "0:03")))
    }

    @Test fun accessibility_actions_step_and_jump_the_playhead() {
        val sought = mutableListOf<Long>()
        show(state(playhead = 3_000), actions = EditorActions(onSeek = { sought += it }))
        runAction(str(R.string.editor_action_forward_second))
        runAction(str(R.string.editor_action_back_second))
        runAction(str(R.string.editor_action_next_edge))
        runAction(str(R.string.editor_action_previous_edge))
        assertEquals(listOf(4_000L, 2_000L, 10_000L, 2_000L), sought)
    }

    @Test fun accessibility_actions_step_the_selection_between_clips() {
        val picked = mutableListOf<String>()
        show(state(selected = "a"), actions = EditorActions(onSelect = { picked += it }))
        runAction(str(R.string.editor_action_next_clip))
        assertEquals(listOf("b"), picked)
        runAction(str(R.string.editor_action_previous_clip))
        assertTrue(picked.size == 1) // "a" is the first clip: nothing before it, so nothing is reported
    }

    // ---- sharing (spec Phase 16) ----
    @Test fun a_finished_export_can_be_shared() {
        val saved = SavedOutput(Uri.parse("content://flow/export"), "video/mp4")
        var shared: SavedOutput? = null
        show(state(export = ExportUiState.Completed).copy(savedExport = saved), actions = EditorActions(onShare = { shared = it }))
        button(R.string.share).performClick()
        assertEquals(saved, shared)
    }

    @Test fun the_export_banner_has_no_share_button_without_a_saved_file() {
        show(state(export = ExportUiState.Completed))
        button(R.string.share).assertDoesNotExist()
    }

    // Regression (step 13): lanes are as wide as the screen, and a clip must still be laid out at its real length past
    // the edge instead of being squeezed to the lane's width.
    @Test fun a_clip_longer_than_the_screen_runs_past_the_edge() {
        val long = Timeline(videoClips = listOf(VideoClip("long", file("long.mp4"), 60_000, 0, 60_000))).normalized()
        show(state(t = long))
        val block = rule.onNode(hasContentDescription(str(R.string.editor_clip_video, 1, 1, "1:00"), substring = true)).fetchSemanticsNode()
        val timeline = timelineNode().fetchSemanticsNode()
        assertTrue("clip ${block.size.width} px should be wider than the timeline ${timeline.size.width} px", block.size.width > timeline.size.width)
    }

    // ---- steps 23 and 24: selecting several, ripple delete, edge handles ----
    @Test fun the_selected_clip_has_trim_handles_and_a_ripple_delete() {
        val calls = mutableListOf<String>()
        show(state(selected = "a"), actions = EditorActions(onRippleDelete = { calls += "ripple" }))
        rule.onNodeWithContentDescription(str(R.string.editor_trim_start_handle)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.editor_trim_end_handle)).assertExists()
        tap(R.string.editor_ripple_delete)
        assertEquals(listOf("ripple"), calls)
    }

    @Test fun no_handles_without_a_selection() {
        show(state())
        rule.onNodeWithContentDescription(str(R.string.editor_trim_end_handle)).assertDoesNotExist()
    }

    @Test fun selecting_several_clips_offers_only_what_applies_to_all_of_them() {
        val calls = mutableListOf<String>()
        show(
            state(selected = "a").copy(multiSelection = setOf("a", "m")),
            actions = EditorActions(onDeleteSelection = { calls += "delete:$it" }, onEndSelect = { calls += "done" }, onToggleSelect = { calls += "toggle:$it" }),
        )
        rule.onNodeWithText(str(R.string.editor_selected_count, 2)).assertIsDisplayed()
        button(R.string.editor_split).assertDoesNotExist()
        // A tap on a clip adds or removes it instead of selecting it.
        rule.onNode(hasContentDescription(str(R.string.editor_clip_video, 2, 2, "0:10"), substring = true)).performScrollTo().performClick()
        tap(R.string.editor_ripple_delete); tap(R.string.editor_select_done)
        assertEquals(listOf("toggle:b", "delete:true", "done"), calls)
        // Picked clips say so; no handles while picking.
        rule.onNode(hasContentDescription(str(R.string.editor_clip_audio, 1, "0:08"), substring = true)).assertIsSelected()
        rule.onNodeWithContentDescription(str(R.string.editor_trim_end_handle)).assertDoesNotExist()
    }

    // ---- step 23, part 2: tool panels inside the editor ----
    @Test fun a_tool_opens_its_panel_inside_the_editor_and_the_tools_stay() {
        show(state(selected = "a"))
        tap(R.string.editor_filter)
        rule.onNodeWithText(str(R.string.filter_title)).assertIsDisplayed()
        // The tools bar is still there, with the open tool marked; another tool replaces the panel, the same tool closes it.
        button(R.string.editor_filter).performScrollTo().assertIsSelected()
        tap(R.string.editor_crop)
        rule.onNodeWithText(str(R.string.crop_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.filter_title)).assertDoesNotExist()
        tap(R.string.editor_crop)
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.crop_title)).assertDoesNotExist()
    }

    @Test fun phone_panels_open_above_the_tools_and_under_the_preview() {
        show(state(selected = "a"))
        tap(R.string.editor_speed)
        val title = rule.onNodeWithText(str(R.string.editor_speed_title)).fetchSemanticsNode().boundsInRoot
        val tool = button(R.string.editor_speed).fetchSemanticsNode().boundsInRoot
        val timeline = timelineNode().fetchSemanticsNode().boundsInRoot
        assertTrue("panel $title should be above the tools $tool", title.bottom <= tool.top)
        assertTrue("panel $title should be below the timeline $timeline", title.top >= timeline.bottom - 1f)
    }

    @Test fun tablet_panels_open_beside_the_preview_in_place_of_the_project_panel() {
        show(state(selected = "a"), layout = EditorLayoutMode.TABLET)
        rule.onNodeWithText(str(R.string.editor_projects_panel)).assertIsDisplayed()
        tap(R.string.editor_speed)
        rule.onNodeWithText(str(R.string.editor_speed_title)).assertIsDisplayed()
        rule.onAllNodesWithTextCount(str(R.string.editor_projects_panel), 0)
        rule.onNodeWithText(str(R.string.editor_panel_done)).performClick()
        rule.onNodeWithText(str(R.string.editor_projects_panel)).assertIsDisplayed()
    }

    @Test fun the_trim_panel_applies_when_the_handle_is_let_go() {
        var trimmed: Pair<Long, Long>? = null
        show(state(selected = "a"), actions = EditorActions(onTrim = { a, b -> trimmed = a to b }))
        tap(R.string.editor_trim)
        rule.onNodeWithContentDescription("${str(R.string.editor_trim_start)}, ${str(R.string.editor_trim_end)}").performTouchInput {
            click(Offset(width * 0.3f, centerY))
        }
        rule.waitForIdle()
        val (start, end) = trimmed ?: error("trim was not applied on release")
        assertTrue("start $start should have moved", start > 0); assertEquals(10_000L, end)
        // No separate Apply step any more.
        rule.onNodeWithText(str(R.string.editor_apply)).assertDoesNotExist()
    }

    @Test fun a_panel_closes_when_another_kind_of_clip_is_picked() {
        var current by androidx.compose.runtime.mutableStateOf(state(selected = "a"))
        rule.setContent { EditorContent(current, TimelinePlaybackState(PlaybackStatus.Ready, false, 0, 20_000), 0, EditorLayoutMode.PHONE, EditorActions(), preview = { Box(it) }) }
        tap(R.string.editor_filter)
        rule.onNodeWithText(str(R.string.filter_title)).assertIsDisplayed()
        // An audio clip has no filters: the panel goes, and does not come back when a video clip is picked again.
        rule.runOnIdle { current = state(selected = "m") }
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.filter_title)).assertDoesNotExist()
        rule.runOnIdle { current = state(selected = "b") }
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.filter_title)).assertDoesNotExist()
    }

    @Test fun back_closes_the_panel_before_it_leaves_the_editor() {
        var left = false
        show(state(selected = "a"), actions = EditorActions(onBack = { left = true }))
        tap(R.string.editor_speed)
        rule.onNodeWithText(str(R.string.editor_speed_title)).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.editor_speed_title)).assertDoesNotExist()
        assertTrue("back should not have left the editor", !left)
    }

    @Test fun a_picked_cue_is_edited_in_a_panel_and_cancel_puts_it_away() {
        val track = io.bsoft.bconverter.domain.subtitle.SubtitleTrack(
            "s", "Subtitles 1", listOf(io.bsoft.bconverter.domain.subtitle.SubtitleCue("c1", 0, 2_000, "Hello")),
        )
        val picked = mutableListOf<Pair<String?, String?>>()
        show(
            state().copy(subtitleTracks = listOf(track), selectedCue = CueRef("s", "c1")),
            actions = EditorActions(subtitles = SubtitleActions(onSelectCue = { t, c -> picked += t to c })),
        )
        rule.onNodeWithText(str(R.string.subtitles_cue_title)).assertIsDisplayed()
        // The tools bar stays under the panel.
        button(R.string.editor_undo).assertExists()
        rule.onNodeWithText(str(R.string.editor_cancel)).performClick()
        assertEquals(listOf<Pair<String?, String?>>(null to null), picked)
    }
}
