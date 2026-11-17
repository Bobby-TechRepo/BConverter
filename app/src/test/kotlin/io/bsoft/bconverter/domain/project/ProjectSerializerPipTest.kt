package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.PipClip
import io.bsoft.bconverter.domain.timeline.PipLayout
import io.bsoft.bconverter.domain.timeline.Timeline
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerPipTest {
    private fun pip(id: String, layout: PipLayout = PipLayout(0.2f, 0.7f, 0.4f, 0.6f), volume: Float = 0.5f) = PipClip(id, TL.source(id), 6_000, 500, 3_500, 2_000, layout, volume)
    private fun stored(t: Timeline) = StoredProject(VideoProject("p", "P", t, TL.exportSettings()), 1, 2)
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project
    private val timeline = TL.timeline(TL.video("a", 10_000)).copy(pipClips = listOf(pip("p"), pip("q", PipLayout(), 1f)))

    @Test fun pip_clips_survive_save_and_load_in_order() {
        val back = decode(ProjectSerializer.encode(stored(timeline))).timeline.pipClips
        assertEquals(timeline.pipClips.map { it.id }, back.map { it.id })
        assertEquals(timeline.pipClips[0].layout, back[0].layout); assertEquals(0.5f, back[0].volume, 0f); assertEquals(2_000L, back[0].startTimeMs); assertEquals(3_000L, back[0].durationMs)
    }
    @Test fun border_and_fades_survive_save_and_load() {
        val styled = timeline.copy(pipClips = listOf(PipClip("s", TL.source("s"), 6_000, 0, 3_000, 0, PipLayout(borderWidth = 0.04f, borderRgb = 0x336699), 1f, 400, 900)))
        val c = decode(ProjectSerializer.encode(stored(styled))).timeline.pipClips.single()
        assertEquals(0.04f, c.layout.borderWidth, 0.0001f); assertEquals(0x336699, c.layout.borderRgb); assertEquals(400L, c.fadeInMs); assertEquals(900L, c.fadeOutMs)
    }
    @Test fun a_chroma_key_survives_save_and_load() {
        val keyed = timeline.copy(pipClips = listOf(PipClip("k", TL.source("k"), 6_000, 0, 3_000, 0, chromaKey = io.bsoft.bconverter.domain.timeline.ChromaKey(0x0000FF, 0.3f, 0.05f))))
        val k = decode(ProjectSerializer.encode(stored(keyed))).timeline.pipClips.single().chromaKey!!
        assertEquals(0x0000FF, k.rgb); assertEquals(0.3f, k.similarity, 0.0001f); assertEquals(0.05f, k.blend, 0.0001f)
    }
    @Test fun keyframes_survive_save_and_load_and_bad_ones_are_dropped() {
        val keys = listOf(io.bsoft.bconverter.domain.timeline.PipKeyframe(0, 0.2f, 0.3f, 0.4f, 0.5f, eased = true), io.bsoft.bconverter.domain.timeline.PipKeyframe(1_000, 0.8f, 0.7f, 0.6f, 1f))
        val keyed = timeline.copy(pipClips = listOf(PipClip("k", TL.source("k"), 6_000, 0, 3_000, 0, keyframes = keys)))
        assertEquals(keys, decode(ProjectSerializer.encode(stored(keyed))).timeline.pipClips.single().keyframes)
        val bad = ProjectSerializer.encode(stored(keyed)).replace("\"w\":0.4", "\"w\":9.0")
        assertEquals(1, decode(bad).timeline.pipClips.single().keyframes.size)
    }
    @Test fun an_end_width_survives_save_and_load() {
        val m = io.bsoft.bconverter.domain.timeline.PipMotion(0.3f, 0.6f, true, 0.5f)
        val moving = timeline.copy(pipClips = listOf(PipClip("m", TL.source("m"), 6_000, 0, 3_000, 0, motion = m)))
        assertEquals(m, decode(ProjectSerializer.encode(stored(moving))).timeline.pipClips.single().motion)
    }
    @Test fun a_shape_survives_save_and_load() {
        val round = timeline.copy(pipClips = listOf(PipClip("r", TL.source("r"), 6_000, 0, 3_000, 0, shape = io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE)))
        assertEquals(io.bsoft.bconverter.domain.timeline.PipShape.CIRCLE, decode(ProjectSerializer.encode(stored(round))).timeline.pipClips.single().shape)
    }
    @Test fun a_speed_survives_save_and_load() {
        val fast = timeline.copy(pipClips = listOf(PipClip("s", TL.source("s"), 6_000, 0, 3_000, 0, speed = 2f)))
        assertEquals(2f, decode(ProjectSerializer.encode(stored(fast))).timeline.pipClips.single().speed)
    }
    @Test fun a_motion_survives_save_and_load() {
        val moving = timeline.copy(pipClips = listOf(PipClip("m", TL.source("m"), 6_000, 0, 3_000, 0, motion = io.bsoft.bconverter.domain.timeline.PipMotion(0.3f, 0.6f, false))))
        assertEquals(io.bsoft.bconverter.domain.timeline.PipMotion(0.3f, 0.6f, false), decode(ProjectSerializer.encode(stored(moving))).timeline.pipClips.single().motion)
    }
    @Test fun a_clip_without_a_key_loads_without_one() { assertEquals(null, decode(ProjectSerializer.encode(stored(timeline))).timeline.pipClips.first().chromaKey) }
    @Test fun an_unstyled_clip_writes_no_style_keys() {
        val text = ProjectSerializer.encode(stored(timeline))
        assertFalse(text.contains("border")); assertFalse(text.contains("fadeInMs")); assertFalse(text.contains("\"key\""))
    }
    @Test fun projects_without_pips_write_nothing_new() { assertFalse(ProjectSerializer.encode(stored(TL.timeline(TL.video("a", 5_000)))).contains("pipClips")) }
    @Test fun a_layout_out_of_range_is_clamped_on_load() {
        val text = ProjectSerializer.encode(stored(timeline)).replace("\"width\":0.4", "\"width\":9").replace("\"x\":0.2", "\"x\":-4")
        val l = decode(text).timeline.pipClips[0].layout
        assertEquals(1f, l.width, 0f); assertEquals(0f, l.centerX, 0f)
    }
    @Test fun a_broken_clip_is_dropped_and_the_rest_load() {
        val text = ProjectSerializer.encode(stored(timeline)).replaceFirst("\"sourceEndMs\":3500", "\"sourceEndMs\":99999999")
        assertEquals(listOf("q"), decode(text).timeline.pipClips.map { it.id })
    }
}
