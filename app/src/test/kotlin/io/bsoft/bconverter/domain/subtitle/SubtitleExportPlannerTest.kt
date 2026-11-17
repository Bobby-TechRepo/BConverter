package io.bsoft.bconverter.domain.subtitle

import io.bsoft.bconverter.domain.model.ContainerFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExportPlannerTest {
    private val cue = SubtitleCue("c", 0, 1000, "Hi")
    private fun track(id: String, mode: SubtitleExportMode = SubtitleExportMode.SOFT, cues: List<SubtitleCue> = listOf(cue), format: SubtitleFormat? = SubtitleFormat.SRT, styled: Boolean = false) =
        SubtitleTrack(id, id, cues, styles = if (styled) mapOf("Default" to SubtitleStyle(bold = true)) else emptyMap(), sourceFormat = format, exportMode = mode)
    private fun plan(c: ContainerFormat, vararg t: SubtitleTrack) = SubtitleExportPlanner.plan(t.toList(), c)

    @Test fun soft_codecs_follow_the_container() {
        assertEquals(SoftSubtitleCodec.MOV_TEXT, plan(ContainerFormat.MP4, track("a")).soft.single().codec)
        assertEquals(SoftSubtitleCodec.MOV_TEXT, plan(ContainerFormat.MOV, track("a")).soft.single().codec)
        assertEquals(SoftSubtitleCodec.MOV_TEXT, plan(ContainerFormat.THREE_GP, track("a")).soft.single().codec)
        assertEquals(SoftSubtitleCodec.SRT, plan(ContainerFormat.MKV, track("a")).soft.single().codec)
        assertEquals(SoftSubtitleCodec.WEBVTT, plan(ContainerFormat.WEBM, track("a")).soft.single().codec)
    }

    @Test fun styled_tracks_keep_their_styling_in_mkv_as_ass() =
        assertEquals(SoftSubtitleCodec.ASS, plan(ContainerFormat.MKV, track("a", styled = true, format = SubtitleFormat.ASS)).soft.single().codec)

    @Test fun styling_lost_in_a_plain_container_is_reported_but_does_not_block() {
        val p = plan(ContainerFormat.MP4, track("a", styled = true, format = SubtitleFormat.ASS))
        assertEquals(listOf(SubtitleExportIssue.StyleSimplified("a", SoftSubtitleCodec.MOV_TEXT)), p.issues); assertTrue(p.blocking.isEmpty()); assertEquals(1, p.soft.size)
    }

    @Test fun containers_without_text_subtitles_block_soft_tracks() {
        listOf(ContainerFormat.AVI, ContainerFormat.MPEG_TS).forEach { c ->
            val p = plan(c, track("a")); assertEquals(listOf(SubtitleExportIssue.SoftNotSupported("a", c)), p.blocking); assertTrue(p.soft.isEmpty())
        }
    }

    @Test fun burned_tracks_work_in_any_container() {
        val p = plan(ContainerFormat.AVI, track("a", SubtitleExportMode.BURNED)); assertEquals(listOf("a"), p.burned.map { it.id }); assertTrue(p.blocking.isEmpty())
    }

    @Test fun every_track_is_in_exactly_one_group_so_nothing_is_duplicated() {
        val p = plan(ContainerFormat.MKV, track("s"), track("b", SubtitleExportMode.BURNED), track("o", SubtitleExportMode.OFF))
        assertEquals(listOf("s"), p.soft.map { it.track.id }); assertEquals(listOf("b"), p.burned.map { it.id })
    }

    @Test fun tracks_without_cues_are_ignored() {
        val p = plan(ContainerFormat.AVI, track("a", cues = emptyList()), track("b", SubtitleExportMode.BURNED, cues = emptyList()))
        assertTrue(p.soft.isEmpty() && p.burned.isEmpty() && p.issues.isEmpty())
    }

    @Test fun a_track_shifted_before_zero_entirely_has_no_cues_to_export() {
        val t = track("a").copy(offsetMs = -5000); assertTrue(plan(ContainerFormat.MKV, t).soft.isEmpty())
    }

    @Test fun order_is_the_order_of_the_tracks() = assertEquals(listOf("x", "y", "z"), plan(ContainerFormat.MKV, track("x"), track("y"), track("z")).soft.map { it.track.id })

    @Test fun the_codec_decides_the_file_format_written() {
        assertEquals(SubtitleFormat.SRT, SoftSubtitleCodec.MOV_TEXT.fileFormat); assertEquals(SubtitleFormat.SRT, SoftSubtitleCodec.SRT.fileFormat)
        assertEquals(SubtitleFormat.ASS, SoftSubtitleCodec.ASS.fileFormat); assertEquals(SubtitleFormat.WEBVTT, SoftSubtitleCodec.WEBVTT.fileFormat)
    }
}
