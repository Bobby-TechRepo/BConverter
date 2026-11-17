package io.bsoft.bconverter.domain.render

import io.bsoft.bconverter.domain.canvas.CanvasFit
import io.bsoft.bconverter.domain.canvas.CanvasSettings
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
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
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareRenderTest {
    private val trimmed = TL.timeline(TL.video("a", 4_000, sourceStart = 1_000), TL.video("b", 6_000))
    private fun project(timeline: io.bsoft.bconverter.domain.timeline.Timeline = trimmed) = TL.project(timeline)
    private fun check(p: VideoProject, overlays: Boolean = false, subtitles: Boolean = false) = HardwareRender.check(p, overlays, subtitles)
    private fun eligible(p: VideoProject) = check(p) is HardwareRender.Verdict.Eligible

    @Test fun a_trim_and_join_project_is_planned_for_the_hardware_encoder() {
        val plan = (check(project()) as HardwareRender.Verdict.Eligible).plan
        assertEquals(listOf(HardwareRender.Clip(File("/tmp/a"), 1_000, 5_000), HardwareRender.Clip(File("/tmp/b"), 0, 6_000)), plan.clips)
        assertEquals(1920 to 1080, plan.width to plan.height)
        assertEquals(VideoCodec.H264, plan.videoCodec)
        assertTrue(plan.keepSound)
        assertEquals(null, plan.videoBitrateKbps)
        assertEquals(10_000L, plan.durationMs)
    }

    @Test fun a_fixed_bitrate_is_passed_on_and_no_sound_is_kept_without_audio() {
        val p = project().copy(export = TL.exportSettings(video = VideoSettings(VideoCodec.HEVC, VideoQuality.Bitrate(6_000)), audio = null))
        val plan = (check(p) as HardwareRender.Verdict.Eligible).plan
        assertEquals(6_000, plan.videoBitrateKbps); assertEquals(VideoCodec.HEVC, plan.videoCodec); assertFalse(plan.keepSound)
    }

    @Test fun formats_the_device_pipeline_does_not_write_stay_with_ffmpeg() {
        assertFalse(eligible(project().copy(export = project().export.copy(container = ContainerFormat.MKV))))
        assertFalse(eligible(project().copy(export = TL.exportSettings(video = VideoSettings(VideoCodec.VP9, VideoQuality.Crf(30))))))
        assertFalse(eligible(project().copy(export = TL.exportSettings(audio = AudioSettings(AudioCodec.OPUS, 128)))))
        assertFalse(eligible(project().copy(export = TL.exportSettings(video = VideoSettings(VideoCodec.H264, VideoQuality.Crf(23), frameRate = 60.0)))))
    }

    @Test fun edits_the_device_pipeline_cannot_reproduce_stay_with_ffmpeg() {
        val crossfade = trimmed.copy(videoClips = trimmed.videoClips.mapIndexed { i, c -> if (i == 1) c.copy(transitionIn = Transition(TransitionType.CROSSFADE, 500)) else c }).normalized()
        assertFalse(eligible(project(crossfade)))
        assertFalse(eligible(project(TL.timeline(TL.video("a", 4_000, speed = 2f)))))
        assertFalse(eligible(project(trimmed.copy(videoClips = trimmed.videoClips.map { it.copy(volume = 0.5f) }))))
        assertFalse(eligible(project(TL.withAudio(trimmed, listOf(TL.audio("m"))))))
        assertFalse(eligible(project().copy(canvas = CanvasSettings(fit = CanvasFit.FILL))))
        assertFalse(check(project(), overlays = true) is HardwareRender.Verdict.Eligible)
        assertFalse(check(project(), subtitles = true) is HardwareRender.Verdict.Eligible)
    }

    @Test fun a_muted_clip_is_fine_when_the_export_has_no_sound() {
        val muted = TL.timeline(TL.video("a", 4_000, muted = true))
        assertFalse(eligible(project(muted)))
        assertTrue(eligible(project(muted).copy(export = TL.exportSettings(audio = null))))
    }
}
