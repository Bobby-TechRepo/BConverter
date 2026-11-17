package io.bsoft.bconverter.domain.project

import io.bsoft.bconverter.domain.timeline.VideoProject
import io.bsoft.bconverter.fakes.TL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectSerializerSoundTest {
    private fun stored(p: VideoProject) = StoredProject(p, 1, 2)
    private fun decode(text: String) = (ProjectSerializer.decode(text) as DecodeResult.Decoded).stored.project
    private val project = VideoProject("p", "P", TL.withAudio(TL.timeline(TL.video("a", 10_000).copy(volume = 0.5f, fadeInMs = 1_000, fadeOutMs = 2_000), TL.video("b", 5_000)), listOf(TL.audio("m").copy(fadeInMs = 300, fadeOutMs = 700, volume = 1.5f))), TL.exportSettings())
    @Test fun volume_and_fades_survive_save_and_load() {
        val p = decode(ProjectSerializer.encode(stored(project)))
        assertEquals(listOf(0.5f, 1f), p.timeline.videoClips.map { it.volume })
        assertEquals(listOf(1_000L, 0L), p.timeline.videoClips.map { it.fadeInMs })
        assertEquals(listOf(2_000L, 0L), p.timeline.videoClips.map { it.fadeOutMs })
        val m = p.timeline.audioClips.single(); assertEquals(300L, m.fadeInMs); assertEquals(700L, m.fadeOutMs); assertEquals(1.5f, m.volume, 0f)
    }
    @Test fun untouched_video_clips_write_nothing_new() {
        val text = ProjectSerializer.encode(stored(VideoProject("p", "P", TL.timeline(TL.video("a", 5_000)), TL.exportSettings())))
        assertFalse(text.contains("fadeInMs")); assertFalse(text.contains("fadeOutMs"))
    }
    @Test fun out_of_range_values_are_clamped_on_load() {
        val text = ProjectSerializer.encode(stored(project)).replace("\"fadeInMs\":1000", "\"fadeInMs\":999999").replace("\"volume\":0.5", "\"volume\":9")
        val c = decode(text).timeline.videoClips[0]
        assertEquals(10_000L, c.fadeInMs); assertEquals(2f, c.volume, 0f)
    }
}
