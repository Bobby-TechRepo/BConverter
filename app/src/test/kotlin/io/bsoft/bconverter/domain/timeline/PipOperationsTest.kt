package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.fakes.TL
import io.bsoft.bconverter.fakes.TL.err
import io.bsoft.bconverter.fakes.TL.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipOperationsTest {
    private fun pip(id: String = "p", start: Long = 1_000, span: Long = 4_000) = PipClip(id, TL.source(id), span + 2_000, 1_000, 1_000 + span, start)
    private val base = TL.withAudio(TL.timeline(TL.video("a", 10_000)), listOf(TL.audio("m")))
    private val withPip = ok(TimelineOperations.addPip(base, pip()))

    @Test fun a_clip_can_be_added() { assertEquals(listOf("p"), withPip.pipClips.map { it.id }); assertTrue(withPip.contains("p")) }
    @Test fun adding_does_not_touch_the_main_video_or_its_length() { assertEquals(10_000L, withPip.videoDurationMs); assertEquals(1, withPip.videoClips.size) }
    @Test fun an_id_in_use_is_refused() {
        assertEquals(EditError.DuplicateId, err(TimelineOperations.addPip(withPip, pip("p"))))
        assertEquals(EditError.DuplicateId, err(TimelineOperations.addPip(withPip, pip("a"))))
    }
    @Test fun the_duration_is_the_source_span() { assertEquals(4_000L, pip().durationMs); assertEquals(5_000L, pip().endTimeMs) }
    @Test fun the_clip_counts_towards_the_clip_total() { assertEquals(base.clipCount + 1, withPip.clipCount) }
    @Test fun it_can_be_moved() { assertEquals(3_500L, ok(TimelineOperations.movePip(withPip, "p", 3_500)).pipClips.single().startTimeMs) }
    @Test fun moving_before_zero_is_refused() { assertEquals(EditError.InvalidStartTime, err(TimelineOperations.movePip(withPip, "p", -1))) }
    @Test fun it_can_be_trimmed() {
        val c = ok(TimelineOperations.trimPip(withPip, "p", 500, 2_500)).pipClips.single()
        assertEquals(2_000L, c.durationMs)
    }
    @Test fun a_bad_trim_is_refused() {
        assertEquals(EditError.InvalidTrimRange, err(TimelineOperations.trimPip(withPip, "p", 2_000, 2_000)))
        assertEquals(EditError.InvalidTrimRange, err(TimelineOperations.trimPip(withPip, "p", 0, 99_000)))
        assertEquals(EditError.InvalidTrimRange, err(TimelineOperations.trimPip(withPip, "p", -5, 1_000)))
    }
    @Test fun the_layout_can_be_set() {
        val l = PipLayout(0.2f, 0.8f, 0.5f, 0.6f)
        assertEquals(l, ok(TimelineOperations.setPipLayout(withPip, "p", l)).pipClips.single().layout)
    }
    @Test fun the_volume_can_be_set_within_range() {
        assertEquals(0f, ok(TimelineOperations.setPipVolume(withPip, "p", 0f)).pipClips.single().volume, 0f)
        assertEquals(EditError.InvalidVolume, err(TimelineOperations.setPipVolume(withPip, "p", 2.5f)))
        assertEquals(EditError.InvalidVolume, err(TimelineOperations.setPipVolume(withPip, "p", Float.NaN)))
    }
    @Test fun an_unknown_clip_is_refused_by_every_pip_operation() {
        val nf = EditError.ClipNotFound("zz")
        assertEquals(nf, err(TimelineOperations.movePip(withPip, "zz", 0))); assertEquals(nf, err(TimelineOperations.trimPip(withPip, "zz", 0, 1)))
        assertEquals(nf, err(TimelineOperations.setPipLayout(withPip, "zz", PipLayout()))); assertEquals(nf, err(TimelineOperations.setPipVolume(withPip, "zz", 1f)))
        assertEquals(nf, err(TimelineOperations.bringPipToFront(withPip, "zz")))
    }
    @Test fun it_can_be_deleted_with_the_ordinary_delete() {
        val t = ok(TimelineOperations.remove(withPip, "p")); assertTrue(t.pipClips.isEmpty()); assertEquals(1, t.videoClips.size)
    }
    @Test fun bringing_to_front_moves_it_last() {
        val two = ok(TimelineOperations.addPip(withPip, pip("q")))
        assertEquals(listOf("q", "p"), ok(TimelineOperations.bringPipToFront(two, "p")).pipClips.map { it.id })
    }
    @Test fun sources_are_mapped_for_pips_too() {
        val mapped = withPip.mapSources { io.bsoft.bconverter.domain.model.MediaSource.FileSource(java.io.File("/x/" + (it as io.bsoft.bconverter.domain.model.MediaSource.FileSource).file.name)) }
        assertEquals("/x/p", (mapped.pipClips.single().source as io.bsoft.bconverter.domain.model.MediaSource.FileSource).file.path.replace('\\', '/'))
    }
    @Test fun normalising_keeps_pips() { assertEquals(withPip.pipClips, withPip.normalized().pipClips) }
    @Test fun a_layout_must_stay_inside_its_limits() {
        for (bad in listOf({ PipLayout(centerX = 1.1f) }, { PipLayout(centerY = -0.1f) }, { PipLayout(width = 0.05f) }, { PipLayout(width = 1.2f) }, { PipLayout(opacity = 0f) }, { PipLayout(centerX = Float.NaN) })) {
            var failed = false
            try { bad() } catch (e: IllegalArgumentException) { failed = true }
            assertTrue(failed)
        }
    }
    @Test fun a_clip_must_have_a_valid_source_range_and_volume() {
        for (bad in listOf({ pip().copy(sourceEndMs = 99_000) }, { pip().copy(startTimeMs = -1) }, { pip().copy(volume = 3f) }, { pip().copy(id = " ") })) {
            var failed = false
            try { bad() } catch (e: IllegalArgumentException) { failed = true }
            assertTrue(failed)
        }
        assertFalse(pip().layout.opacity < 1f)
    }
    @Test fun the_commands_undo() {
        val e = DefaultTimelineEditor(base)
        e.addPipClip(pip()); e.movePipClip("p", 2_000); e.setPipLayout("p", PipLayout(width = 0.5f)); e.setPipVolume("p", 0.5f); e.trimPipClip("p", 0, 1_000)
        e.undo(); e.undo(); e.undo()
        assertEquals(2_000L, e.state.value.timeline.pipClips.single().startTimeMs); assertEquals(PipLayout(), e.state.value.timeline.pipClips.single().layout)
        e.undo(); e.undo(); assertTrue(e.state.value.timeline.pipClips.isEmpty())
        e.redo(); assertEquals(1, e.state.value.timeline.pipClips.size)
    }
}
