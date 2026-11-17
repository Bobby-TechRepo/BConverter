package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.Resolution
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.model.VideoSettings
import io.bsoft.bconverter.domain.timeline.Transition
import io.bsoft.bconverter.domain.timeline.TransitionType
import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedRenderTest {
    private val work = File("/tmp/work")

    private fun clips(n: Int, crossfadeBefore: Set<Int> = emptySet()) = TL.timeline(
        *(0 until n).map { i ->
            TL.video("c$i", 4_000).let { if (i in crossfadeBefore) it.copy(transitionIn = Transition(TransitionType.CROSSFADE, 500)) else it }
        }.toTypedArray(),
    )

    private fun planFor(project: VideoProject, maxClips: Int = 2): SegmentedRender.Plan? {
        val single = (RenderPlanner.plan(project) as RenderPlanResult.Success).plan
        return SegmentedRender.plan(project, single, work, "out", maxClips)
    }

    @Test fun short_projects_stay_one_pass() {
        assertEquals(listOf(0 until 6), SegmentedRender.partRanges(clips(6)))
        assertFalse(SegmentedRender.needsParts(TL.project(clips(6))))
        assertNull(planFor(TL.project(clips(2))))
    }

    @Test fun long_projects_are_cut_into_full_parts() {
        assertEquals(listOf(0 until 6, 6 until 12, 12 until 13), SegmentedRender.partRanges(clips(13)))
        assertTrue(SegmentedRender.needsParts(TL.project(clips(7))))
    }

    @Test fun a_part_never_ends_inside_a_transition() {
        // A crossfade before clip 2 keeps clips 1 and 2 together, so the first part runs on to the next plain cut.
        assertEquals(listOf(0 until 3, 3 until 5, 5 until 6), SegmentedRender.partRanges(clips(6, crossfadeBefore = setOf(2)), maxClips = 2))
        // With transitions everywhere there is nowhere to cut: one part.
        assertEquals(listOf(0 until 4), SegmentedRender.partRanges(clips(4, crossfadeBefore = setOf(1, 2, 3)), maxClips = 2))
    }

    @Test fun parts_render_their_clips_alone_at_the_canvas_size_with_pcm_sound() {
        val project = TL.project(TL.withAudio(clips(5), listOf(TL.audio("music", 8_000))))
        val plan = planFor(project)!!
        assertEquals(3, plan.parts.size)
        assertEquals(listOf(listOf("c0", "c1"), listOf("c2", "c3"), listOf("c4")), plan.parts.map { p -> p.project.timeline.videoClips.map { it.id } })
        assertEquals(listOf(8_000L, 8_000L, 4_000L), plan.parts.map { it.durationMs })
        assertEquals(listOf(0L, 4_000L), plan.parts[1].project.timeline.videoClips.map { it.startTimeMs })
        plan.parts.forEach { part ->
            assertTrue(part.project.timeline.audioClips.isEmpty())
            assertTrue(part.project.timeline.pipClips.isEmpty())
            assertEquals(ContainerFormat.MKV, part.project.export.container)
            assertEquals(AudioCodec.PCM, part.project.export.audio!!.codec)
            assertEquals(Resolution(1920, 1080), part.project.export.video.resolution)
            assertEquals(30.0, part.project.export.video.frameRate!!, 0.0)
        }
        assertEquals(File(work, "out.part2.mkv"), plan.parts[1].file)
        assertTrue(plan.partsHaveSound)
        assertEquals(20_000L, plan.durationMs)
    }

    @Test fun the_final_pass_keeps_everything_that_spans_the_timeline_over_the_joined_parts() {
        val project = TL.project(TL.withAudio(clips(5), listOf(TL.audio("music", 8_000, start = 3_000))))
        val plan = planFor(project)!!
        val final = plan.finalProject
        val base = final.timeline.videoClips.single()
        assertEquals(io.bsoft.bconverter.domain.model.MediaSource.FileSource(plan.joinedFile), base.source)
        assertEquals(project.timeline.videoDurationMs, base.durationMs)
        assertEquals(project.timeline.audioClips, final.timeline.audioClips)
        assertEquals(Resolution(1920, 1080), final.export.video.resolution)
        // The final pass plans like any project: one input for the joined parts, then the music.
        val finalPlan = (RenderPlanner.plan(final) as RenderPlanResult.Success).plan
        assertEquals(plan.joinedFile, finalPlan.inputs.first().file)
        assertEquals(2, finalPlan.inputs.size)
        assertEquals(20_000L, finalPlan.durationMs)
        assertTrue(finalPlan.originalAudio != null)
    }

    @Test fun a_part_whose_clips_are_all_silent_still_gets_a_sound_track() {
        val timeline = clips(4).let { t -> t.copy(videoClips = t.videoClips.mapIndexed { i, c -> if (i >= 2) c.copy(audioMuted = true) else c }) }
        val plan = planFor(TL.project(timeline))!!
        val silentPart = plan.parts[1].project
        assertNull((RenderPlanner.plan(silentPart) as RenderPlanResult.Success).plan.originalAudio)
        val kept = (RenderPlanner.plan(silentPart, keepSoundTrack = true) as RenderPlanResult.Success).plan.originalAudio!!
        assertTrue(kept.all { it.inputIndex == null })
    }

    @Test fun without_original_sound_the_parts_are_silent_and_the_final_pass_adds_only_the_audio_clips() {
        val project = TL.project(clips(4), export = TL.exportSettings(keepOriginalAudio = false))
        val plan = planFor(project)!!
        assertFalse(plan.partsHaveSound)
        assertNull(plan.parts.first().project.export.audio)
    }

    @Test fun the_intermediate_quality_is_higher_than_the_exports() {
        val canvas = Canvas(1280, 720, 25.0)
        assertEquals(VideoQuality.Crf(17), SegmentedRender.intermediateVideo(VideoSettings(VideoCodec.H264, VideoQuality.Crf(23)), canvas).quality)
        assertEquals(VideoQuality.Crf(0), SegmentedRender.intermediateVideo(VideoSettings(VideoCodec.H264, VideoQuality.Crf(3)), canvas).quality)
        assertEquals(VideoQuality.Bitrate(8_000), SegmentedRender.intermediateVideo(VideoSettings(VideoCodec.MPEG4, VideoQuality.Bitrate(4_000)), canvas).quality)
        val v = SegmentedRender.intermediateVideo(VideoSettings(VideoCodec.H264, VideoQuality.Crf(23), level = "4.0"), canvas)
        assertNull(v.level); assertEquals(Resolution(1280, 720), v.resolution); assertEquals(25.0, v.frameRate!!, 0.0)
    }

    @Test fun the_concat_list_quotes_every_path() {
        // Absolute paths differ by platform (the unit tests also run on Windows), so the expectation is built from them.
        val one = File("/a/one.mkv"); val quoted = File("/a/it's.mkv")
        val quotedPath = quoted.absolutePath.replace("'", "'\\''")
        assertEquals("file '${one.absolutePath}'\nfile '$quotedPath'\n", SegmentedRender.concatList(listOf(one, quoted)))
        assertTrue(quotedPath.endsWith("it'\\''s.mkv"))
    }
}
